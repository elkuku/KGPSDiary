<?php

declare(strict_types=1);

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\Tag;
use App\Entity\Track;
use App\Entity\User;
use App\Entity\Waypoint;
use App\Repository\UserRepository;
use App\Repository\WaypointRepository;
use App\Tests\Photo\TestImages;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\DomCrawler\Field\ChoiceFormField;
use Symfony\Component\DomCrawler\Field\FileFormField;
use Symfony\Component\Filesystem\Filesystem;

/**
 * The web map and the waypoint pages.
 */
class WaypointControllerTest extends WebTestCase
{
    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    protected function tearDown(): void
    {
        foreach (['Photo', 'Waypoint', 'Category', 'Tag', 'Track'] as $entity) {
            $this->entityManager()->createQuery(\sprintf('DELETE FROM App\Entity\%s', $entity))->execute();
        }

        $photoDirectory = static::getContainer()->getParameter('app.photo_dir');
        self::assertIsString($photoDirectory);
        new Filesystem()->remove($photoDirectory);

        parent::tearDown();
    }

    public function testPagesNeedASignIn(): void
    {
        foreach (['/map', '/map/waypoints.geojson', '/waypoints/new'] as $path) {
            $this->client->request('GET', $path);
            self::assertResponseRedirects('/login', 302, $path);
        }

        $waypoint = $this->waypoint('user', 'Secret');
        $this->client->request('GET', '/waypoints/'.$waypoint->getId());
        self::assertResponseRedirects('/login');
    }

    public function testTheMapPageConfiguresTheMap(): void
    {
        $this->client->loginUser($this->user('user'));
        $this->client->request('GET', '/map?latitude=-0.2&longitude=-78.5');

        self::assertResponseIsSuccessful();
        $map = $this->client->getCrawler()->filter('[data-controller="map"]');
        self::assertSame('/map/waypoints.geojson', $map->attr('data-map-features-url-value'));
        self::assertSame('/waypoints/new', $map->attr('data-map-new-url-value'));
        self::assertSame('[-78.5,-0.2]', $map->attr('data-map-center-value'));
    }

    public function testTheMapShowsOnlyTheUsersWaypoints(): void
    {
        $category = new Category()->setName('Monuments')->setColor('#ff8800')->setOwner($this->user('user'));
        $this->entityManager()->persist($category);
        $mine = $this->waypoint('user', 'Mitad del Mundo', -0.0022, -78.4558)
            ->setCategory($category)
            ->setVisitedAt(new \DateTimeImmutable('2026-10-31'));
        $this->entityManager()->flush();
        $this->waypoint('admin', "Admin's place");

        $this->client->loginUser($this->user('user'));
        $this->client->request('GET', '/map/waypoints.geojson');

        self::assertResponseIsSuccessful();
        self::assertResponseHeaderSame('Content-Type', 'application/geo+json');
        $json = \json_decode((string) $this->client->getResponse()->getContent(), true, flags: \JSON_THROW_ON_ERROR);
        self::assertIsArray($json);
        self::assertSame('FeatureCollection', $json['type']);
        self::assertSame([[
            'type' => 'Feature',
            'geometry' => ['type' => 'Point', 'coordinates' => [-78.4558, -0.0022]],
            'properties' => [
                'id' => $mine->getId(),
                'title' => 'Mitad del Mundo',
                'visitedAt' => '2026-10-31',
                'category' => 'Monuments',
                'categoryId' => $category->getId(),
                'tagIds' => [],
                'color' => '#ff8800',
                'url' => '/waypoints/'.$mine->getId(),
            ],
        ]], $json['features']);
    }

    public function testCreatesAWaypointWhereTheMapWasClicked(): void
    {
        $user = $this->user('user');
        $tag = new Tag()->setName('Ecuador')->setOwner($user);
        $theirTag = new Tag()->setName('Not mine')->setOwner($this->user('admin'));
        $this->entityManager()->persist($tag);
        $this->entityManager()->persist($theirTag);
        $this->entityManager()->flush();

        $this->client->loginUser($user);
        $crawler = $this->client->request('GET', '/waypoints/new?latitude=-0.0022&longitude=-78.4558');

        self::assertResponseIsSuccessful();
        self::assertInputValueSame('waypoint[latitude]', '-0.002200');
        self::assertInputValueSame('waypoint[longitude]', '-78.455800');
        self::assertInputValueSame('waypoint[visitedAt]', new \DateTimeImmutable('today')->format('Y-m-d'));
        // Only the user's own tags to choose from
        self::assertSelectorTextContains('form', 'Ecuador');
        self::assertSelectorTextNotContains('form', 'Not mine');

        $form = $crawler->selectButton('Save')->form([
            'waypoint[title]' => 'Mitad del Mundo',
            'waypoint[description]' => 'The equator.',
        ]);
        $ecuador = $form['waypoint[tags][0]'];
        self::assertInstanceOf(ChoiceFormField::class, $ecuador);
        $ecuador->tick();
        $this->client->submit($form);

        $waypoint = $this->waypoints()->findOneBy(['title' => 'Mitad del Mundo']);
        self::assertInstanceOf(Waypoint::class, $waypoint);
        self::assertResponseRedirects('/waypoints/'.$waypoint->getId());
        self::assertSame($user->id, $waypoint->getOwner()?->id);
        self::assertSame(-0.0022, $waypoint->getLatitude());
        self::assertSame(['Ecuador'], $waypoint->getTags()->map(static fn (Tag $tag): string => $tag->getName())->toArray());

        $this->client->followRedirect();
        self::assertSelectorTextContains('h2', 'Mitad del Mundo');
        self::assertSelectorTextContains('.alert-success', 'The waypoint has been saved.');
    }

    public function testValidatesTheForm(): void
    {
        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/waypoints/new');

        $this->client->submit($crawler->selectButton('Save')->form([
            'waypoint[title]' => '',
            'waypoint[latitude]' => '95',
            'waypoint[longitude]' => '0',
        ]));

        self::assertResponseStatusCodeSame(422);
        self::assertSelectorCount(2, '.invalid-feedback');
    }

    public function testEditsAndDeletesAWaypoint(): void
    {
        $waypoint = $this->waypoint('user', 'Old title');
        $id = $waypoint->getId();

        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', \sprintf('/waypoints/%d/edit', $id));
        $this->client->submit($crawler->selectButton('Save')->form(['waypoint[title]' => 'New title']));

        self::assertResponseRedirects('/waypoints/'.$id);
        $crawler = $this->client->followRedirect();
        self::assertSelectorTextContains('h2', 'New title');

        $this->client->submit($crawler->selectButton('Delete')->form());

        self::assertResponseRedirects('/map');
        $this->entityManager()->clear();
        self::assertNull($this->waypoints()->find($id));
    }

    public function testDeletingNeedsTheFormsToken(): void
    {
        $waypoint = $this->waypoint('user', 'Keep me');

        $this->client->loginUser($this->user('user'));
        $this->client->request('POST', \sprintf('/waypoints/%d/delete', $waypoint->getId()), ['_token' => 'forged']);

        self::assertResponseRedirects('/waypoints/'.$waypoint->getId());
        $this->entityManager()->clear();
        self::assertNotNull($this->waypoints()->find($waypoint->getId()));
    }

    public function testOtherUsersWaypointsAreNotFound(): void
    {
        $theirs = $this->waypoint('admin', "Admin's place");

        $this->client->loginUser($this->user('user'));

        foreach (['GET /waypoints/%d', 'GET /waypoints/%d/edit', 'POST /waypoints/%d/edit', 'POST /waypoints/%d/delete'] as $request) {
            [$method, $path] = \explode(' ', $request);
            $this->client->request($method, \sprintf($path, $theirs->getId()));
            self::assertResponseStatusCodeSame(404, $request);
        }
    }

    public function testUploadsShowsAndDeletesPhotos(): void
    {
        $waypoint = $this->waypoint('user', 'Mitad del Mundo');

        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/waypoints/'.$waypoint->getId());
        self::assertSelectorTextContains('main', 'No photos yet.');

        $form = $crawler->selectButton('Upload')->form();
        $file = $form['photos[0]'];
        self::assertInstanceOf(FileFormField::class, $file);
        $file->upload(TestImages::jpeg(1200, 900));
        $this->client->submit($form);

        self::assertResponseRedirects('/waypoints/'.$waypoint->getId());
        $crawler = $this->client->followRedirect();
        self::assertSelectorTextContains('.alert-success', 'The photos have been added.');
        $thumbnail = (string) $crawler->filter('main img')->attr('src');
        self::assertMatchesRegularExpression('#^/photos/\d+/thumb$#', $thumbnail);

        $this->client->request('GET', $thumbnail);
        self::assertResponseIsSuccessful();
        self::assertResponseHeaderSame('Content-Type', 'image/jpeg');

        // Not for other users
        $this->client->loginUser($this->user('admin'));
        $this->client->request('GET', $thumbnail);
        self::assertResponseStatusCodeSame(404);

        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/waypoints/'.$waypoint->getId());
        $this->client->submit($crawler->filter('form[action$="/delete"]')->first()->form());
        $this->client->followRedirect();
        self::assertSelectorTextContains('main', 'No photos yet.');
    }

    public function testListsAndSearchesTheUsersWaypoints(): void
    {
        $tag = new Tag()->setName('Ecuador')->setOwner($this->user('user'));
        $this->entityManager()->persist($tag);
        $this->waypoint('user', 'Basílica del Voto Nacional')->addTag($tag)->setVisitedAt(new \DateTimeImmutable('2026-11-02'));
        $this->waypoint('user', 'Mercado Central')->setDescription('Lunch at the market')->setVisitedAt(new \DateTimeImmutable('2026-11-01'));
        $this->waypoint('admin', 'Basilica of someone else');
        $this->entityManager()->flush();

        $this->client->loginUser($this->user('user'));

        $crawler = $this->client->request('GET', '/waypoints');
        self::assertResponseIsSuccessful();
        self::assertSame(['Basílica del Voto Nacional', 'Mercado Central'], $crawler->filter('.list-group strong')->each(static fn ($node): string => $node->text()));

        // Without accents and case, in all words
        $crawler = $this->client->request('GET', '/waypoints?q=basilica+ECUADOR');
        self::assertSame(['Basílica del Voto Nacional'], $crawler->filter('.list-group strong')->each(static fn ($node): string => $node->text()));
        self::assertSelectorTextContains('main', '1 of 2 waypoints');

        // The description too
        $crawler = $this->client->request('GET', '/waypoints?q=lunch');
        self::assertSame(['Mercado Central'], $crawler->filter('.list-group strong')->each(static fn ($node): string => $node->text()));

        $this->client->request('GET', '/waypoints?q=nowhere');
        self::assertSelectorTextContains('main', 'No waypoint matches the search.');
    }

    public function testListsTracksAndDrawsThemOnTheMap(): void
    {
        $track = new Track()
            ->setName('Walk to the market')
            ->setPoints([[0.0, -78.5, 1_793_000_000], [0.01, -78.5, 1_793_000_600]])
            ->setOwner($this->user('user'));
        $this->entityManager()->persist($track);
        $theirs = new Track()->setName('Secret walk')->setPoints([[1, 1, 1_793_000_000], [2, 2, 1_793_000_100]])->setOwner($this->user('admin'));
        $this->entityManager()->persist($theirs);
        $this->entityManager()->flush();

        $this->client->loginUser($this->user('user'));

        $crawler = $this->client->request('GET', '/tracks');
        self::assertSelectorTextContains('.list-group', 'Walk to the market');
        self::assertSelectorTextContains('.list-group', '1.11 km');
        self::assertSelectorTextContains('.list-group', '10 min');
        self::assertSelectorTextNotContains('main', 'Secret walk');

        $this->client->request('GET', '/map/tracks.geojson');
        $json = \json_decode((string) $this->client->getResponse()->getContent(), true, flags: \JSON_THROW_ON_ERROR);
        self::assertIsArray($json);
        self::assertEquals([[-78.5, 0.0], [-78.5, 0.01]], $json['features'][0]['geometry']['coordinates'] ?? null);
        self::assertCount(1, (array) $json['features']);

        $this->client->submit($crawler->selectButton('Delete')->form());
        self::assertResponseRedirects('/tracks');
        $this->client->followRedirect();
        self::assertSelectorTextContains('main', 'No tracks yet.');
    }

    public function testThePagesAreTranslated(): void
    {
        $this->client->loginUser($this->user('user'));
        $this->client->request('GET', '/map', server: ['HTTP_ACCEPT_LANGUAGE' => 'de-DE,de;q=0.9']);

        self::assertSelectorTextContains('h2', 'Karte');
    }

    private function waypoint(string $owner, string $title, float $latitude = 1.0, float $longitude = 2.0): Waypoint
    {
        $waypoint = new Waypoint()
            ->setOwner($this->user($owner))
            ->setTitle($title)
            ->setLatitude($latitude)
            ->setLongitude($longitude);

        $this->entityManager()->persist($waypoint);
        $this->entityManager()->flush();

        return $waypoint;
    }

    private function user(string $identifier): User
    {
        $users = static::getContainer()->get(UserRepository::class);
        self::assertInstanceOf(UserRepository::class, $users);
        $user = $users->findOneBy(['identifier' => $identifier]);
        self::assertInstanceOf(User::class, $user);

        return $user;
    }

    private function waypoints(): WaypointRepository
    {
        $waypoints = static::getContainer()->get(WaypointRepository::class);
        self::assertInstanceOf(WaypointRepository::class, $waypoints);

        return $waypoints;
    }

    private function entityManager(): EntityManagerInterface
    {
        $entityManager = static::getContainer()->get(EntityManagerInterface::class);
        self::assertInstanceOf(EntityManagerInterface::class, $entityManager);

        return $entityManager;
    }
}
