<?php

declare(strict_types=1);

namespace App\Tests\Photo;

use App\Photo\PhotoStorage;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;
use Symfony\Component\Filesystem\Filesystem;

class PhotoStorageTest extends TestCase
{
    private string $directory;

    private PhotoStorage $storage;

    protected function setUp(): void
    {
        $this->directory = \sys_get_temp_dir().'/kgps-photos-'.\bin2hex(\random_bytes(4));
        $this->storage = new PhotoStorage($this->directory);
    }

    protected function tearDown(): void
    {
        new Filesystem()->remove($this->directory);
    }

    public function testStoresAScaledImageAndAThumbnail(): void
    {
        [$filename, $width, $height] = $this->storage->store(TestImages::jpeg(3000, 1500));

        self::assertMatchesRegularExpression('/^[0-9a-f]{32}\.jpg$/', $filename);
        self::assertSame([2048, 1024], [$width, $height]);
        self::assertSame([2048, 1024], \array_slice((array) \getimagesize($this->storage->path($filename, PhotoStorage::FULL)), 0, 2));
        self::assertSame([400, 200], \array_slice((array) \getimagesize($this->storage->path($filename, PhotoStorage::THUMB)), 0, 2));
    }

    public function testKeepsSmallImagesAsTheyAre(): void
    {
        [, $width, $height] = $this->storage->store(TestImages::jpeg(300, 200));

        self::assertSame([300, 200], [$width, $height]);
    }

    public function testTurnsPhotosUpright(): void
    {
        // A phone photo taken upright: stored landscape, EXIF orientation 6 (rotate 90° clockwise)
        [, $width, $height] = $this->storage->store(TestImages::jpeg(400, 300, 6));

        self::assertSame([300, 400], [$width, $height]);
    }

    public function testRejectsFilesThatAreNoImages(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->storage->store(TestImages::text());
    }

    public function testRemovesBothFiles(): void
    {
        [$filename] = $this->storage->store(TestImages::jpeg(500, 500));

        $this->storage->remove($filename);

        self::assertFileDoesNotExist($this->storage->path($filename, PhotoStorage::FULL));
        self::assertFileDoesNotExist($this->storage->path($filename, PhotoStorage::THUMB));
    }

    #[DataProvider('orientations')]
    public function testReadsTheJpegOrientation(?int $orientation, int $expected): void
    {
        self::assertSame($expected, PhotoStorage::jpegOrientation((string) \file_get_contents(TestImages::jpeg(10, 10, $orientation))));
    }

    /**
     * @return iterable<string, array{int|null, int}>
     */
    public static function orientations(): iterable
    {
        yield 'no EXIF' => [null, 1];
        yield 'upright' => [1, 1];
        yield 'rotated' => [6, 6];
        yield 'upside down' => [3, 3];
        yield 'invalid' => [42, 1];
    }

    public function testNoJpegHasNoOrientation(): void
    {
        self::assertSame(1, PhotoStorage::jpegOrientation("\x89PNG..."));
    }
}
