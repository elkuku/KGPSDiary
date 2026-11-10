<?php

declare(strict_types=1);

namespace App\Tests\Api;

use App\Security\FakeGoogleIdTokenVerifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\Filesystem\Filesystem;

/**
 * API requests as the fixture users "user" and "admin", signed in with fake Google ID tokens.
 */
abstract class ApiTestCase extends WebTestCase
{
    protected KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    protected function tearDown(): void
    {
        foreach (['Photo', 'Waypoint', 'Category', 'Tag'] as $entity) {
            $this->entityManager()->createQuery(\sprintf('DELETE FROM App\Entity\%s', $entity))->execute();
        }

        // DQL deletes leave the photo files
        $photoDirectory = static::getContainer()->getParameter('app.photo_dir');
        self::assertIsString($photoDirectory);
        new Filesystem()->remove($photoDirectory);

        $this->entityManager()->createQuery('UPDATE App\Entity\User u SET u.googleId = NULL')->execute();

        parent::tearDown();
    }

    /**
     * @param array<string, mixed>|null $body
     *
     * @return array<string, mixed>
     */
    protected function api(string $method, string $uri, ?array $body = null, ?string $as = 'user'): array
    {
        $server = ['HTTP_ACCEPT' => 'application/ld+json'];

        if (null !== $as) {
            $server['HTTP_AUTHORIZATION'] = 'Bearer '.FakeGoogleIdTokenVerifier::token('gid-'.$as, $as);
        }

        if (null !== $body) {
            $server['CONTENT_TYPE'] = 'PATCH' === $method ? 'application/merge-patch+json' : 'application/ld+json';
        }

        $this->client->request($method, $uri, server: $server, content: null === $body ? null : \json_encode($body, \JSON_THROW_ON_ERROR));

        $content = (string) $this->client->getResponse()->getContent();
        if ('' === $content) {
            return [];
        }

        $json = \json_decode($content, true, flags: \JSON_THROW_ON_ERROR);
        self::assertIsArray($json);

        /* @var array<string, mixed> $json */
        return $json;
    }

    /**
     * @param array<string, mixed> $body
     *
     * @return string the new record's IRI
     */
    protected function create(string $collection, array $body, string $as = 'user'): string
    {
        $json = $this->api('POST', $collection, $body, $as);
        self::assertResponseStatusCodeSame(201, \json_encode($json, \JSON_THROW_ON_ERROR));
        self::assertIsString($json['@id']);

        return $json['@id'];
    }

    /**
     * The members of a collection answer.
     *
     * @param array<string, mixed> $json
     *
     * @return list<array<string, mixed>>
     */
    protected static function members(array $json): array
    {
        self::assertIsArray($json['member'] ?? null);

        /** @var list<array<string, mixed>> $members */
        $members = $json['member'];

        return $members;
    }

    /**
     * @param array<string, mixed> $json
     *
     * @return list<string>
     */
    protected static function titles(array $json): array
    {
        return \array_map(static fn (array $member): string => (string) ($member['title'] ?? $member['name'] ?? ''), self::members($json));
    }

    protected function entityManager(): EntityManagerInterface
    {
        $entityManager = static::getContainer()->get(EntityManagerInterface::class);
        self::assertInstanceOf(EntityManagerInterface::class, $entityManager);

        return $entityManager;
    }
}
