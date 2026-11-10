<?php

declare(strict_types=1);

namespace App\Tests\Api;

use App\Entity\User;
use App\Repository\UserRepository;
use App\Security\FakeGoogleIdTokenVerifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * GET /api/me with a Google ID token as the bearer token (FakeGoogleIdTokenVerifier in the test env).
 */
class MeApiTest extends WebTestCase
{
    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    protected function tearDown(): void
    {
        $this->entityManager()->createQuery("DELETE FROM App\\Entity\\User u WHERE u.identifier LIKE '%@example.com'")->execute();
        $this->entityManager()->createQuery('UPDATE App\Entity\User u SET u.googleId = NULL')->execute();

        parent::tearDown();
    }

    public function testRequiresToken(): void
    {
        $this->client->request('GET', '/api/me');

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
        self::assertResponseHeaderSame('WWW-Authenticate', 'Bearer');
        self::assertSame('Authentication required.', $this->responseJson()['detail']);
    }

    public function testRejectsInvalidToken(): void
    {
        $this->get('not-a-google-token');

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
        self::assertSame('Invalid ID token.', $this->responseJson()['detail']);
    }

    public function testRejectsUnverifiedEmail(): void
    {
        $this->get(FakeGoogleIdTokenVerifier::token('gid-unverified', 'unverified@example.com', false));

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
        self::assertSame('Email address is not verified.', $this->responseJson()['detail']);
        self::assertNotInstanceOf(User::class, $this->users()->findOneBy(['identifier' => 'unverified@example.com']));
    }

    public function testRegistersUnknownGoogleAccounts(): void
    {
        $this->get(FakeGoogleIdTokenVerifier::token('gid-new', 'new@example.com'));

        self::assertResponseIsSuccessful();
        $json = $this->responseJson();
        self::assertSame('new@example.com', $json['identifier']);
        self::assertSame('ROLE_USER', $json['role']);

        $user = $this->users()->findOneBy(['googleId' => 'gid-new']);
        self::assertInstanceOf(User::class, $user);
        self::assertSame($user->id, $json['id']);
    }

    public function testLinksTheExistingAccountWithTheSameEmail(): void
    {
        $admin = $this->users()->findOneBy(['identifier' => 'admin']);
        self::assertInstanceOf(User::class, $admin);

        // The fixture users' identifiers are no email addresses: sign in as "admin"
        $this->get(FakeGoogleIdTokenVerifier::token('gid-admin', 'admin'));

        self::assertResponseIsSuccessful();
        self::assertSame($admin->id, $this->responseJson()['id']);
        self::assertSame('ROLE_ADMIN', $this->responseJson()['role']);

        $this->entityManager()->clear();
        self::assertSame('gid-admin', $this->users()->findOneBy(['identifier' => 'admin'])?->getGoogleId());
    }

    public function testDocsArePublic(): void
    {
        $this->client->request('GET', '/api/docs.jsonopenapi');

        self::assertResponseIsSuccessful();
    }

    private function get(string $idToken): void
    {
        $this->client->request('GET', '/api/me', server: [
            'HTTP_AUTHORIZATION' => 'Bearer '.$idToken,
            'HTTP_ACCEPT' => 'application/ld+json',
        ]);
    }

    /**
     * @return array<string, mixed>
     */
    private function responseJson(): array
    {
        $json = \json_decode((string) $this->client->getResponse()->getContent(), true, flags: \JSON_THROW_ON_ERROR);
        self::assertIsArray($json);

        /* @var array<string, mixed> $json */
        return $json;
    }

    private function users(): UserRepository
    {
        $repository = static::getContainer()->get(UserRepository::class);
        self::assertInstanceOf(UserRepository::class, $repository);

        return $repository;
    }

    private function entityManager(): EntityManagerInterface
    {
        $entityManager = static::getContainer()->get(EntityManagerInterface::class);
        self::assertInstanceOf(EntityManagerInterface::class, $entityManager);

        return $entityManager;
    }
}
