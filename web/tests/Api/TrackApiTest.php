<?php

declare(strict_types=1);

namespace App\Tests\Api;

use PHPUnit\Framework\Attributes\DataProvider;

class TrackApiTest extends ApiTestCase
{
    /** About 1.11 km north along the meridian, in 10 minutes. */
    private const array POINTS = [
        [0.0, -78.5, 1_793_000_000],
        [0.005, -78.5, 1_793_000_300],
        [0.01, -78.5, 1_793_000_600],
    ];

    protected function tearDown(): void
    {
        $this->entityManager()->createQuery('DELETE FROM App\Entity\Track')->execute();

        parent::tearDown();
    }

    public function testCreatesATrackAndMeasuresIt(): void
    {
        $iri = $this->create('/api/tracks', ['name' => ' Walk to the market ', 'points' => self::POINTS]);

        $json = $this->api('GET', $iri);

        self::assertSame('Walk to the market', $json['name']);
        self::assertSame(3, $json['pointCount']);
        self::assertEquals(self::POINTS, $json['points']);
        self::assertSame('2026-10-26T07:33:20+00:00', $json['startedAt']);
        self::assertSame('2026-10-26T07:43:20+00:00', $json['endedAt']);
        self::assertEqualsWithDelta(1111.95, $json['distance'], 0.2);
    }

    /**
     * @param array<mixed> $points
     */
    #[DataProvider('invalidPoints')]
    public function testRejectsInvalidPoints(array $points, string $message): void
    {
        $json = $this->api('POST', '/api/tracks', ['name' => 'Bad', 'points' => $points]);

        self::assertResponseStatusCodeSame(422);
        self::assertIsArray($json['violations']);
        self::assertSame('points', $json['violations'][0]['propertyPath'] ?? null);
        self::assertSame($message, $json['violations'][0]['message'] ?? null);
    }

    /**
     * @return iterable<string, array{array<mixed>, string}>
     */
    public static function invalidPoints(): iterable
    {
        yield 'one point' => [[[0, 0, 1_793_000_000]], 'A track needs at least 2 points.'];
        yield 'no time' => [[[0, 0], [1, 1]], 'Each point is [latitude, longitude, time] with the time in Unix seconds.'];
        yield 'latitude out of range' => [[[0, 0, 1_793_000_000], [91, 0, 1_793_000_001]], 'Each point is [latitude, longitude, time] with the time in Unix seconds.'];
        yield 'backwards in time' => [[[0, 0, 1_793_000_100], [0, 1, 1_793_000_000]], 'The points must be in the order they were recorded.'];
    }

    public function testOnlyTheNameChanges(): void
    {
        $iri = $this->create('/api/tracks', ['name' => 'Walk', 'points' => self::POINTS]);

        $json = $this->api('PATCH', $iri, ['name' => 'Morning walk', 'points' => [[1, 1, 1], [2, 2, 2]]]);

        self::assertResponseIsSuccessful();
        self::assertSame('Morning walk', $json['name']);
        self::assertEquals(self::POINTS, $json['points']);
    }

    public function testUsersOnlySeeTheirOwnTracks(): void
    {
        $iri = $this->create('/api/tracks', ['name' => 'Mine', 'points' => self::POINTS]);
        $this->create('/api/tracks', ['name' => 'Theirs', 'points' => self::POINTS], as: 'admin');

        self::assertSame(['Mine'], self::titles($this->api('GET', '/api/tracks')));

        $this->api('GET', $iri, as: 'admin');
        self::assertResponseStatusCodeSame(404);

        $this->api('DELETE', $iri, as: 'admin');
        self::assertResponseStatusCodeSame(404);

        $this->api('DELETE', $iri);
        self::assertResponseStatusCodeSame(204);
    }
}
