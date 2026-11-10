<?php

declare(strict_types=1);

namespace App\Photo;

use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Component\Filesystem\Filesystem;

/**
 * The photo files: each upload is stored as a JPEG of at most 2048 pixels and a thumbnail of at
 * most 400, turned upright and without the original's metadata (EXIF, GPS).
 */
final readonly class PhotoStorage
{
    public const int FULL_SIZE = 2048;

    public const int THUMB_SIZE = 400;

    public const string THUMB = 'thumb';

    public const string FULL = 'full';

    private Filesystem $filesystem;

    public function __construct(#[Autowire(param: 'app.photo_dir')] private string $directory)
    {
        $this->filesystem = new Filesystem();
    }

    /**
     * Stores the image in [file] (JPEG, PNG, WebP or AVIF).
     *
     * @return array{string, int, int} the stored file's name, and the full image's width and height
     *
     * @throws \InvalidArgumentException for a file that is no readable image
     */
    public function store(string $file): array
    {
        $content = (string) \file_get_contents($file);
        $image = '' === $content ? false : @\imagecreatefromstring($content);

        if (false === $image) {
            throw new \InvalidArgumentException('The file is no readable image.');
        }

        $image = $this->upright($image, self::jpegOrientation($content));
        $filename = \bin2hex(\random_bytes(16)).'.jpg';
        $this->filesystem->mkdir($this->directory);

        $full = $this->scaled($image, self::FULL_SIZE);
        \imagejpeg($full, $this->path($filename, self::FULL), 85);
        \imagejpeg($this->scaled($image, self::THUMB_SIZE), $this->path($filename, self::THUMB), 80);

        return [$filename, \imagesx($full), \imagesy($full)];
    }

    public function path(string $filename, string $size): string
    {
        return \sprintf('%s/%s%s', $this->directory, self::THUMB === $size ? 'thumb-' : '', $filename);
    }

    public function remove(string $filename): void
    {
        $this->filesystem->remove([$this->path($filename, self::FULL), $this->path($filename, self::THUMB)]);
    }

    /**
     * The image scaled down to at most [max] pixels on its longer side.
     */
    private function scaled(\GdImage $image, int $max): \GdImage
    {
        $width = \imagesx($image);
        $height = \imagesy($image);
        $factor = \min(1, $max / \max($width, $height));

        if ($factor >= 1) {
            return $image;
        }

        $newWidth = \max(1, (int) \round($width * $factor));
        $newHeight = \max(1, (int) \round($height * $factor));
        $scaled = \imagecreatetruecolor($newWidth, $newHeight);

        \imagecopyresampled($scaled, $image, 0, 0, 0, 0, $newWidth, $newHeight, $width, $height);

        return $scaled;
    }

    /**
     * The image rotated and mirrored as its EXIF orientation (1 to 8) says.
     */
    private function upright(\GdImage $image, int $orientation): \GdImage
    {
        if (\in_array($orientation, [2, 4, 5, 7], true)) {
            \imageflip($image, \IMG_FLIP_HORIZONTAL);
        }

        $angle = match ($orientation) {
            3, 4 => 180,
            5, 6 => 270,
            7, 8 => 90,
            default => 0,
        };

        if (0 === $angle) {
            return $image;
        }

        $rotated = \imagerotate($image, $angle, 0);

        return false === $rotated ? $image : $rotated;
    }

    /**
     * The EXIF orientation of a JPEG (1 = upright), read without the exif extension.
     */
    public static function jpegOrientation(string $jpeg): int
    {
        if (!\str_starts_with($jpeg, "\xFF\xD8")) {
            return 1;
        }

        $offset = 2;

        // Segments: 0xFF, the marker, a 2 byte length; up to the start of the scan (0xDA)
        while ("\xFF" === ($jpeg[$offset] ?? null) && "\xDA" !== ($jpeg[$offset + 1] ?? "\xDA")) {
            $segmentLength = self::number('n', $jpeg, $offset + 2);

            if (null === $segmentLength) {
                break;
            }

            // APP1 with "Exif\0\0"
            if ("\xE1" === $jpeg[$offset + 1] && "Exif\0\0" === \substr($jpeg, $offset + 4, 6)) {
                return self::tiffOrientation(\substr($jpeg, $offset + 10, $segmentLength - 8));
            }

            $offset += 2 + $segmentLength;
        }

        return 1;
    }

    /**
     * The Orientation tag (0x0112) of the first IFD of EXIF's TIFF structure.
     */
    private static function tiffOrientation(string $tiff): int
    {
        // Byte order: "II" little endian, "MM" big endian
        [$short, $long] = \str_starts_with($tiff, 'II') ? ['v', 'V'] : ['n', 'N'];
        $ifd = self::number($long, $tiff, 4) ?? \PHP_INT_MAX;
        $entries = self::number($short, $tiff, $ifd) ?? 0;

        for ($entry = $ifd + 2; $entry < $ifd + 2 + $entries * 12; $entry += 12) {
            if (0x0112 === self::number($short, $tiff, $entry)) {
                $orientation = self::number($short, $tiff, $entry + 8) ?? 1;

                return $orientation >= 1 && $orientation <= 8 ? $orientation : 1;
            }
        }

        return 1;
    }

    /**
     * The number in [format] (an unpack() code) at [offset]; null outside of [data].
     */
    private static function number(string $format, string $data, int $offset): ?int
    {
        $size = \in_array($format, ['V', 'N'], true) ? 4 : 2;

        if ($offset < 0 || $offset + $size > \strlen($data)) {
            return null;
        }

        $values = \unpack($format, $data, $offset);

        return \is_array($values) && \is_int($values[1] ?? null) ? $values[1] : null;
    }
}
