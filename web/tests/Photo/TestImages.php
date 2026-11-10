<?php

declare(strict_types=1);

namespace App\Tests\Photo;

/**
 * Test images, written to temporary files.
 */
final class TestImages
{
    /**
     * A JPEG of [width] x [height] pixels, with an EXIF orientation when [orientation] is given.
     */
    public static function jpeg(int $width, int $height, ?int $orientation = null): string
    {
        $image = \imagecreatetruecolor(\max(1, $width), \max(1, $height));
        \imagefill($image, 0, 0, (int) \imagecolorallocate($image, 30, 120, 200));

        \ob_start();
        \imagejpeg($image);
        $jpeg = (string) \ob_get_clean();

        if (null !== $orientation) {
            // Little-endian TIFF with one IFD entry: Orientation (0x0112), SHORT, count 1
            $tiff = 'II'.\pack('v', 42).\pack('V', 8)
                .\pack('v', 1)
                .\pack('v', 0x0112).\pack('v', 3).\pack('V', 1).\pack('v', $orientation).\pack('v', 0)
                .\pack('V', 0);
            $app1 = "Exif\0\0".$tiff;
            $jpeg = "\xFF\xD8\xFF\xE1".\pack('n', \strlen($app1) + 2).$app1.\substr($jpeg, 2);
        }

        return self::file($jpeg, '.jpg');
    }

    public static function text(): string
    {
        return self::file('This is no image.', '.txt');
    }

    private static function file(string $content, string $extension): string
    {
        $path = \sys_get_temp_dir().'/kgps-test-'.\bin2hex(\random_bytes(6)).$extension;
        \file_put_contents($path, $content);

        return $path;
    }
}
