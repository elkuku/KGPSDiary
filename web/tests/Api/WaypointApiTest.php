<?php

declare(strict_types=1);

namespace App\Tests\Api;

use PHPUnit\Framework\Attributes\DataProvider;

class WaypointApiTest extends ApiTestCase
{
    /** Quito, the Mitad del Mundo monument. */
    private const array MITAD_DEL_MUNDO = ['title' => 'Mitad del Mundo', 'latitude' => -0.0022, 'longitude' => -78.4558];

    public function testRequiresSignIn(): void
    {
        $this->api('GET', '/api/waypoints', as: null);
        self::assertResponseStatusCodeSame(401);

        $this->api('POST', '/api/waypoints', self::MITAD_DEL_MUNDO, as: null);
        self::assertResponseStatusCodeSame(401);
    }

    public function testCreatesAndReadsAWaypoint(): void
    {
        $category = $this->create('/api/categories', ['name' => 'Monuments', 'color' => '#FF8800', 'icon' => 'flag']);
        $tag = $this->create('/api/tags', ['name' => 'Ecuador']);

        $iri = $this->create('/api/waypoints', [
            ...self::MITAD_DEL_MUNDO,
            'description' => "The equator.\nWith a museum.",
            'visitedAt' => '2026-10-31',
            'category' => $category,
            'tags' => [$tag],
        ]);

        $json = $this->api('GET', $iri);

        self::assertResponseIsSuccessful();
        self::assertSame('Mitad del Mundo', $json['title']);
        self::assertSame("The equator.\nWith a museum.", $json['description']);
        self::assertSame(-0.0022, $json['latitude']);
        self::assertSame(-78.4558, $json['longitude']);
        self::assertSame('2026-10-31', $json['visitedAt']);
        self::assertIsArray($json['category']);
        self::assertSame('Monuments', $json['category']['name']);
        self::assertSame('#ff8800', $json['category']['color']);
        self::assertIsArray($json['tags']);
        self::assertSame('Ecuador', $json['tags'][0]['name'] ?? null);
        self::assertArrayHasKey('createdAt', $json);
        self::assertArrayHasKey('updatedAt', $json);
        self::assertArrayNotHasKey('owner', $json);
    }

    public function testUpdatesAWaypoint(): void
    {
        $iri = $this->create('/api/waypoints', self::MITAD_DEL_MUNDO);
        $createdAt = $this->api('GET', $iri)['createdAt'];
        $tag = $this->create('/api/tags', ['name' => 'Revisit']);

        // updatedAt has a precision of one second
        \sleep(1);
        $json = $this->api('PATCH', $iri, ['description' => 'Bring a hat.', 'visitedAt' => null, 'tags' => [$tag]]);

        self::assertResponseIsSuccessful();
        self::assertSame('Mitad del Mundo', $json['title']);
        self::assertSame('Bring a hat.', $json['description']);
        self::assertNull($json['visitedAt'] ?? null);
        self::assertCount(1, (array) $json['tags']);
        self::assertSame($createdAt, $json['createdAt']);
        self::assertGreaterThan($createdAt, $json['updatedAt']);
    }

    public function testDeletesAWaypoint(): void
    {
        $iri = $this->create('/api/waypoints', self::MITAD_DEL_MUNDO);

        $this->api('DELETE', $iri);
        self::assertResponseStatusCodeSame(204);

        $this->api('GET', $iri);
        self::assertResponseStatusCodeSame(404);
    }

    /**
     * @param array<string, mixed> $body
     */
    #[DataProvider('invalidWaypoints')]
    public function testValidates(array $body, string $property): void
    {
        $json = $this->api('POST', '/api/waypoints', $body);

        self::assertResponseStatusCodeSame(422);
        self::assertIsArray($json['violations']);
        self::assertContains($property, \array_column($json['violations'], 'propertyPath'));
    }

    /**
     * @return iterable<string, array{array<string, mixed>, string}>
     */
    public static function invalidWaypoints(): iterable
    {
        yield 'no title' => [['title' => ' ', 'latitude' => 0, 'longitude' => 0], 'title'];
        yield 'no latitude' => [['title' => 'A', 'longitude' => 0], 'latitude'];
        yield 'latitude out of range' => [['title' => 'A', 'latitude' => 91, 'longitude' => 0], 'latitude'];
        yield 'longitude out of range' => [['title' => 'A', 'latitude' => 0, 'longitude' => -181], 'longitude'];
    }

    public function testUsersOnlySeeTheirOwnWaypoints(): void
    {
        $mine = $this->create('/api/waypoints', self::MITAD_DEL_MUNDO);
        $theirs = $this->create('/api/waypoints', ['title' => "Admin's place", 'latitude' => 1, 'longitude' => 1], as: 'admin');

        self::assertSame(['Mitad del Mundo'], self::titles($this->api('GET', '/api/waypoints')));
        self::assertSame(1, $this->api('GET', '/api/waypoints')['totalItems']);

        // Not even admins see other users' diaries
        self::assertSame(["Admin's place"], self::titles($this->api('GET', '/api/waypoints', as: 'admin')));

        foreach (['GET' => null, 'PATCH' => ['title' => 'Mine now'], 'DELETE' => null] as $method => $body) {
            $this->api($method, $theirs, $body);
            self::assertResponseStatusCodeSame(404, $method);
        }

        $this->api('GET', $mine, as: 'admin');
        self::assertResponseStatusCodeSame(404);
    }

    public function testRejectsOtherUsersCategoriesAndTags(): void
    {
        $theirCategory = $this->create('/api/categories', ['name' => 'Secret'], as: 'admin');
        $theirTag = $this->create('/api/tags', ['name' => 'Secret'], as: 'admin');

        $this->api('POST', '/api/waypoints', [...self::MITAD_DEL_MUNDO, 'category' => $theirCategory]);
        self::assertResponseStatusCodeSame(400);

        $this->api('POST', '/api/waypoints', [...self::MITAD_DEL_MUNDO, 'tags' => [$theirTag]]);
        self::assertResponseStatusCodeSame(400);
    }

    public function testFiltersByBoundingBox(): void
    {
        $this->create('/api/waypoints', self::MITAD_DEL_MUNDO);
        $this->create('/api/waypoints', ['title' => 'Galápagos', 'latitude' => -0.9538, 'longitude' => -90.9656]);
        $this->create('/api/waypoints', ['title' => 'Fiji', 'latitude' => -17.7, 'longitude' => 178.0]);
        $this->create('/api/waypoints', ['title' => 'Samoa', 'latitude' => -13.8, 'longitude' => -172.1]);

        self::assertSame(['Mitad del Mundo'], self::titles($this->api('GET', '/api/waypoints?bbox=-79,-1,-78,1')));

        // Across the antimeridian
        $titles = self::titles($this->api('GET', '/api/waypoints?bbox=170,-20,-170,-10'));
        \sort($titles);
        self::assertSame(['Fiji', 'Samoa'], $titles);
    }

    #[DataProvider('invalidBoxes')]
    public function testRejectsInvalidBoundingBoxes(string $bbox): void
    {
        $this->api('GET', '/api/waypoints?bbox='.$bbox);

        self::assertResponseStatusCodeSame(400);
    }

    /**
     * @return iterable<string, array{string}>
     */
    public static function invalidBoxes(): iterable
    {
        yield 'three numbers' => ['1,2,3'];
        yield 'no numbers' => ['a,b,c,d'];
        yield 'latitude out of range' => ['0,-91,1,1'];
        yield 'south above north' => ['0,10,1,5'];
    }

    public function testFiltersByCategoryAndTag(): void
    {
        $monuments = $this->create('/api/categories', ['name' => 'Monuments']);
        $food = $this->create('/api/categories', ['name' => 'Food']);
        $ecuador = $this->create('/api/tags', ['name' => 'Ecuador']);

        $this->create('/api/waypoints', [...self::MITAD_DEL_MUNDO, 'category' => $monuments, 'tags' => [$ecuador]]);
        $this->create('/api/waypoints', ['title' => 'Café', 'latitude' => 0, 'longitude' => 0, 'category' => $food]);

        self::assertSame(['Mitad del Mundo'], self::titles($this->api('GET', '/api/waypoints?category='.$monuments)));
        self::assertSame(['Café'], self::titles($this->api('GET', '/api/waypoints?category='.$food)));
        self::assertSame(['Mitad del Mundo'], self::titles($this->api('GET', '/api/waypoints?tag='.$ecuador)));
    }

    public function testFiltersByUpdateTime(): void
    {
        $old = $this->create('/api/waypoints', self::MITAD_DEL_MUNDO);
        $this->create('/api/waypoints', ['title' => 'New', 'latitude' => 0, 'longitude' => 0]);
        $this->entityManager()->getConnection()->executeStatement(
            "UPDATE waypoint SET updated_at = '2026-01-01 00:00:00+00' WHERE id = ?",
            [(int) \basename($old)],
        );

        self::assertSame(['New'], self::titles($this->api('GET', '/api/waypoints?updatedSince='.\urlencode('2026-06-01T00:00:00Z'))));
        self::assertCount(2, self::members($this->api('GET', '/api/waypoints?updatedSince=2025-12-31T23:00:00-02:00')));

        $this->api('GET', '/api/waypoints?updatedSince=yesterday-ish');
        self::assertResponseStatusCodeSame(400);
    }

    public function testListsTheNewestVisitsFirst(): void
    {
        $this->create('/api/waypoints', ['title' => 'Older', 'latitude' => 0, 'longitude' => 0, 'visitedAt' => '2026-01-01']);
        $this->create('/api/waypoints', ['title' => 'Newer', 'latitude' => 0, 'longitude' => 0, 'visitedAt' => '2026-02-01']);

        self::assertSame(['Newer', 'Older'], self::titles($this->api('GET', '/api/waypoints')));
    }
}
