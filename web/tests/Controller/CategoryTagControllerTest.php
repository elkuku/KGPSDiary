<?php

declare(strict_types=1);

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\Tag;
use App\Entity\User;
use App\Entity\Waypoint;
use App\Repository\CategoryRepository;
use App\Repository\TagRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

/**
 * Managing categories and tags on the web, and the map's filters.
 */
class CategoryTagControllerTest extends WebTestCase
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
        foreach (['/categories', '/tags'] as $path) {
            $this->client->request('GET', $path);
            self::assertResponseRedirects('/login', 302, $path);
        }
    }

    public function testAddsACategory(): void
    {
        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/categories');
        self::assertSelectorTextContains('main', 'You have no categories yet.');

        $this->client->submit($crawler->selectButton('Add category')->form([
            'category[name]' => 'Monuments',
            'category[color]' => '#FF8800',
            'category[icon]' => 'museum',
        ]));

        self::assertResponseRedirects('/categories');
        $this->client->followRedirect();
        self::assertSelectorTextContains('.list-group', 'Monuments');
        self::assertSelectorTextContains('.list-group', 'no waypoints');

        $category = $this->categories()->findOneBy(['name' => 'Monuments']);
        self::assertInstanceOf(Category::class, $category);
        self::assertSame('#ff8800', $category->getColor());
        self::assertSame('museum', $category->getIcon());
        self::assertSame($this->user('user')->id, $category->getOwner()?->id);
    }

    public function testNamesAreUniquePerUser(): void
    {
        $this->category('admin', 'Food');
        $this->category('user', 'Food');

        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/categories');
        $this->client->submit($crawler->selectButton('Add category')->form(['category[name]' => 'Food']));

        self::assertResponseStatusCodeSame(422);
        self::assertSelectorTextContains('.invalid-feedback', 'You already use this name.');
    }

    public function testEditsAndDeletesACategoryKeepingItsWaypoints(): void
    {
        $category = $this->category('user', 'Food');
        $waypoint = new Waypoint()->setOwner($this->user('user'))->setTitle('Café')->setLatitude(0)->setLongitude(0)->setCategory($category);
        $this->entityManager()->persist($waypoint);
        $this->entityManager()->flush();

        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/categories');
        self::assertSelectorTextContains('.list-group', 'one waypoint');

        $crawler = $this->client->request('GET', \sprintf('/categories/%d/edit', $category->getId()));
        $this->client->submit($crawler->selectButton('Save')->form(['category[name]' => 'Restaurants']));
        self::assertResponseRedirects('/categories');

        $crawler = $this->client->followRedirect();
        self::assertSelectorTextContains('.list-group', 'Restaurants');

        $this->client->submit($crawler->selectButton('Delete')->form());
        self::assertResponseRedirects('/categories');

        $this->entityManager()->clear();
        self::assertNull($this->categories()->find($category->getId()));
        $kept = $this->entityManager()->find(Waypoint::class, $waypoint->getId());
        self::assertInstanceOf(Waypoint::class, $kept);
        self::assertNull($kept->getCategory());
    }

    public function testAddsEditsAndDeletesATag(): void
    {
        $this->client->loginUser($this->user('user'));
        $crawler = $this->client->request('GET', '/tags');
        $this->client->submit($crawler->selectButton('Add tag')->form(['tag[name]' => 'Hiking']));
        self::assertResponseRedirects('/tags');

        $tag = $this->tags()->findOneBy(['name' => 'Hiking']);
        self::assertInstanceOf(Tag::class, $tag);

        $crawler = $this->client->request('GET', \sprintf('/tags/%d/edit', $tag->getId()));
        $this->client->submit($crawler->selectButton('Save')->form(['tag[name]' => 'Trekking']));
        $crawler = $this->client->followRedirect();
        self::assertSelectorTextContains('.list-group', 'Trekking');

        $this->client->submit($crawler->selectButton('Delete')->form());
        $this->entityManager()->clear();
        self::assertNull($this->tags()->find($tag->getId()));
    }

    public function testOtherUsersCategoriesAndTagsAreNotFound(): void
    {
        $category = $this->category('admin', 'Secret');
        $tag = new Tag()->setName('Secret')->setOwner($this->user('admin'));
        $this->entityManager()->persist($tag);
        $this->entityManager()->flush();

        $this->client->loginUser($this->user('user'));
        $this->client->request('GET', '/categories');
        self::assertSelectorTextNotContains('main', 'Secret');

        foreach ([
            'GET /categories/%d/edit' => $category->getId(),
            'POST /categories/%d/delete' => $category->getId(),
            'GET /tags/%d/edit' => $tag->getId(),
            'POST /tags/%d/delete' => $tag->getId(),
        ] as $request => $id) {
            [$method, $path] = \explode(' ', $request);
            $this->client->request($method, \sprintf($path, $id));
            self::assertResponseStatusCodeSame(404, $request);
        }
    }

    public function testTheMapOffersTheUsersCategoriesAndTagsAsFilters(): void
    {
        $this->category('user', 'Food');
        $this->category('admin', 'Secret');
        $tag = new Tag()->setName('Lunch')->setOwner($this->user('user'));
        $this->entityManager()->persist($tag);
        $this->entityManager()->flush();

        $this->client->loginUser($this->user('user'));
        $this->client->request('GET', '/map');

        self::assertSelectorTextContains('[data-map-target="category"]', 'Food');
        self::assertSelectorTextNotContains('[data-map-target="category"]', 'Secret');
        self::assertSelectorTextContains('[data-map-target="tag"]', 'Lunch');
    }

    private function category(string $owner, string $name): Category
    {
        $category = new Category()->setName($name)->setOwner($this->user($owner));
        $this->entityManager()->persist($category);
        $this->entityManager()->flush();

        return $category;
    }

    private function user(string $identifier): User
    {
        $users = static::getContainer()->get(UserRepository::class);
        self::assertInstanceOf(UserRepository::class, $users);
        $user = $users->findOneBy(['identifier' => $identifier]);
        self::assertInstanceOf(User::class, $user);

        return $user;
    }

    private function categories(): CategoryRepository
    {
        $repository = static::getContainer()->get(CategoryRepository::class);
        self::assertInstanceOf(CategoryRepository::class, $repository);

        return $repository;
    }

    private function tags(): TagRepository
    {
        $repository = static::getContainer()->get(TagRepository::class);
        self::assertInstanceOf(TagRepository::class, $repository);

        return $repository;
    }

    private function entityManager(): EntityManagerInterface
    {
        $entityManager = static::getContainer()->get(EntityManagerInterface::class);
        self::assertInstanceOf(EntityManagerInterface::class, $entityManager);

        return $entityManager;
    }
}
