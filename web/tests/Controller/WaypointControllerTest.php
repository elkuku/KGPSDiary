<?php

declare(strict_types=1);

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\Tag;
use App\Entity\User;
use App\Entity\Waypoint;
use App\Repository\UserRepository;
use App\Repository\WaypointRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\DomCrawler\Field\ChoiceFormField;

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
        foreach (['Waypoint', 'Category', 'Tag'] as $entity) {
            $this->entityManager()->createQuery(\sprintf('DELETE FROM App\Entity\%s', $entity))->execute();
        }

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
