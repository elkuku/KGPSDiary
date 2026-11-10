<?php

declare(strict_types=1);

namespace App\Tests\Api;

use App\Entity\Photo;
use App\Photo\PhotoStorage;
use App\Security\FakeGoogleIdTokenVerifier;
use App\Tests\Photo\TestImages;
use Symfony\Component\HttpFoundation\File\UploadedFile;

class PhotoApiTest extends ApiTestCase
{
    public function testUploadsAPhotoToAWaypoint(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'Mitad del Mundo', 'latitude' => 0, 'longitude' => -78.45]);
        $updatedAt = $this->api('GET', $waypoint)['updatedAt'];
        \sleep(1);

        $json = $this->upload($waypoint, TestImages::jpeg(3000, 2000));

        self::assertResponseStatusCodeSame(201);
        self::assertIsInt($json['id']);
        self::assertSame(2048, $json['width']);
        self::assertSame(1365, $json['height']);
        self::assertSame(\sprintf('/api/photos/%d/file', $json['id']), $json['url']);
        self::assertSame(\sprintf('/api/photos/%d/file?size=thumb', $json['id']), $json['thumbnailUrl']);

        $read = $this->api('GET', $waypoint);
        self::assertIsArray($read['photos']);
        self::assertCount(1, $read['photos']);
        self::assertSame($json['url'], $read['photos'][0]['url'] ?? null);
        // The waypoint changed, for clients that sync with updatedSince
        self::assertGreaterThan($updatedAt, $read['updatedAt']);
    }

    public function testServesTheFilesToTheOwnerOnly(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);
        $photo = $this->upload($waypoint, TestImages::jpeg(800, 600));

        $this->client->request('GET', (string) $photo['thumbnailUrl'], server: $this->auth('user'));
        self::assertResponseIsSuccessful();
        self::assertResponseHeaderSame('Content-Type', 'image/jpeg');
        self::assertSame([400, 300], \array_slice((array) \getimagesizefromstring($this->client->getInternalResponse()->getContent()), 0, 2));
        self::assertStringContainsString('private', (string) $this->client->getResponse()->headers->get('Cache-Control'));

        $this->client->request('GET', (string) $photo['url'], server: $this->auth('user'));
        self::assertResponseIsSuccessful();

        $this->client->request('GET', (string) $photo['url'], server: $this->auth('admin'));
        self::assertResponseStatusCodeSame(404);

        $this->client->request('GET', (string) $photo['url']);
        self::assertResponseStatusCodeSame(401);
    }

    public function testRejectsFilesThatAreNoImages(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);

        $json = $this->upload($waypoint, TestImages::text());

        self::assertResponseStatusCodeSame(422);
        self::assertSame('Upload a JPEG, PNG, WebP or AVIF image.', $json['detail']);
    }

    public function testRejectsUploadsWithoutAFile(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);

        $this->client->request('POST', $waypoint.'/photos', server: $this->auth('user'));

        self::assertResponseStatusCodeSame(422);
    }

    public function testNobodyElseAddsPhotos(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);

        $this->upload($waypoint, TestImages::jpeg(100, 100), 'admin');
        self::assertResponseStatusCodeSame(404);

        $this->client->request('POST', $waypoint.'/photos', files: ['file' => $this->uploaded(TestImages::jpeg(100, 100))]);
        self::assertResponseStatusCodeSame(401);
    }

    public function testDeletesAPhotoAndItsFiles(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);
        $photo = $this->upload($waypoint, TestImages::jpeg(100, 100));
        $files = $this->files((int) $photo['id']);

        $this->api('DELETE', '/api/photos/'.$photo['id'], as: 'admin');
        self::assertResponseStatusCodeSame(404);

        $updatedAt = $this->api('GET', $waypoint)['updatedAt'];
        \sleep(1);

        $this->api('DELETE', '/api/photos/'.$photo['id']);
        self::assertResponseStatusCodeSame(204);
        self::assertGreaterThan($updatedAt, $this->api('GET', $waypoint)['updatedAt']);

        self::assertSame([], $this->api('GET', $waypoint)['photos']);
        foreach ($files as $file) {
            self::assertFileDoesNotExist($file);
        }
    }

    public function testDeletingTheWaypointDeletesThePhotoFiles(): void
    {
        $waypoint = $this->create('/api/waypoints', ['title' => 'A', 'latitude' => 0, 'longitude' => 0]);
        $photo = $this->upload($waypoint, TestImages::jpeg(100, 100));
        $files = $this->files((int) $photo['id']);
        self::assertFileExists($files[0]);

        $this->api('DELETE', $waypoint);
        self::assertResponseStatusCodeSame(204);

        foreach ($files as $file) {
            self::assertFileDoesNotExist($file);
        }
    }

    /**
     * @return array<string, mixed>
     */
    private function upload(string $waypoint, string $file, string $as = 'user'): array
    {
        $this->client->request('POST', $waypoint.'/photos', files: ['file' => $this->uploaded($file)], server: $this->auth($as));

        $json = \json_decode((string) $this->client->getResponse()->getContent(), true, flags: \JSON_THROW_ON_ERROR);
        self::assertIsArray($json);

        /* @var array<string, mixed> $json */
        return $json;
    }

    private function uploaded(string $file): UploadedFile
    {
        return new UploadedFile($file, \basename($file), test: true);
    }

    /**
     * @return array<string, string>
     */
    private function auth(string $as): array
    {
        return ['HTTP_AUTHORIZATION' => 'Bearer '.FakeGoogleIdTokenVerifier::token('gid-'.$as, $as), 'HTTP_ACCEPT' => 'application/json'];
    }

    /**
     * @return list<string> the full image and the thumbnail
     */
    private function files(int $id): array
    {
        $photo = $this->entityManager()->find(Photo::class, $id);
        self::assertInstanceOf(Photo::class, $photo);
        $storage = static::getContainer()->get(PhotoStorage::class);
        self::assertInstanceOf(PhotoStorage::class, $storage);

        return [$storage->path($photo->getFilename(), PhotoStorage::FULL), $storage->path($photo->getFilename(), PhotoStorage::THUMB)];
    }
}
