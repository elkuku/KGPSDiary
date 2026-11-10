<?php

declare(strict_types=1);

namespace App\Tests\Security;

use App\Repository\UserRepository;
use App\Security\FakeGoogleIdTokenVerifier;
use App\Security\GoogleApiTokenAuthenticator;
use App\Security\GoogleUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Security\Core\Exception\AuthenticationException;

class GoogleApiTokenAuthenticatorTest extends TestCase
{
    private GoogleApiTokenAuthenticator $authenticator;

    protected function setUp(): void
    {
        $this->authenticator = new GoogleApiTokenAuthenticator(
            new FakeGoogleIdTokenVerifier(),
            new GoogleUserResolver($this->createStub(UserRepository::class), $this->createStub(EntityManagerInterface::class)),
        );
    }

    public function testSupportsBearerTokensOnly(): void
    {
        self::assertTrue($this->authenticator->supports($this->request('Bearer abc')));
        self::assertFalse($this->authenticator->supports($this->request('Basic abc')));
        self::assertFalse($this->authenticator->supports(Request::create('/api/me')));
    }

    public function testRejectsAnEmptyToken(): void
    {
        $this->expectException(AuthenticationException::class);
        $this->expectExceptionMessageIsOrContains('Missing ID token.');
        $this->authenticator->authenticate($this->request('Bearer  '));
    }

    public function testFailureIsAProblemResponse(): void
    {
        $response = $this->authenticator->onAuthenticationFailure(Request::create('/api/me'), new AuthenticationException());

        self::assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());
        self::assertSame('application/problem+json', $response->headers->get('Content-Type'));
    }

    private function request(string $authorization): Request
    {
        return Request::create('/api/me', server: ['HTTP_AUTHORIZATION' => $authorization]);
    }
}
