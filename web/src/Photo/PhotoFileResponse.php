<?php

declare(strict_types=1);

namespace App\Photo;

use App\Entity\Photo;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * A photo's file for its owner. The files never change, so browsers and the app may keep them,
 * but only privately.
 */
final readonly class PhotoFileResponse
{
    public function __construct(private PhotoStorage $storage)
    {
    }

    public function create(Photo $photo, string $size): BinaryFileResponse
    {
        $path = $this->storage->path($photo->getFilename(), PhotoStorage::THUMB === $size ? PhotoStorage::THUMB : PhotoStorage::FULL);

        if (!\is_file($path)) {
            throw new NotFoundHttpException('The photo file is missing.');
        }

        $response = new BinaryFileResponse($path, headers: ['Content-Type' => 'image/jpeg'], autoEtag: true);
        $response->setPrivate();
        $response->setMaxAge(31_536_000);
        $response->setImmutable();

        return $response;
    }
}
