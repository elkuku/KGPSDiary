<?php

declare(strict_types=1);

namespace App\Photo;

use App\Entity\Photo;
use App\Entity\Waypoint;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\Validator\Constraints\Image;
use Symfony\Component\Validator\Validator\ValidatorInterface;

/**
 * Adds uploaded photos to a waypoint, for the API and the web.
 */
final readonly class PhotoUploader
{
    public const int MAX_PHOTOS = 50;

    public function __construct(
        private PhotoStorage $storage,
        private ValidatorInterface $validator,
        private EntityManagerInterface $entityManager,
    ) {
    }

    /**
     * @throws PhotoRejectedException with a message for the user
     */
    public function add(Waypoint $waypoint, ?UploadedFile $file): Photo
    {
        if (!$file instanceof UploadedFile) {
            throw new PhotoRejectedException('Send the image as the "file" field of a multipart form.');
        }

        if ($waypoint->getPhotos()->count() >= self::MAX_PHOTOS) {
            throw new PhotoRejectedException(\sprintf('A waypoint can have at most %d photos.', self::MAX_PHOTOS));
        }

        $violations = $this->validator->validate($file, new Image(
            maxSize: '20M',
            mimeTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/avif'],
            mimeTypesMessage: 'Upload a JPEG, PNG, WebP or AVIF image.',
        ));

        if (\count($violations) > 0) {
            throw new PhotoRejectedException((string) $violations->get(0)->getMessage());
        }

        try {
            [$filename, $width, $height] = $this->storage->store($file->getPathname());
        } catch (\InvalidArgumentException $invalidArgumentException) {
            throw new PhotoRejectedException($invalidArgumentException->getMessage(), $invalidArgumentException->getCode(), previous: $invalidArgumentException);
        }

        $photo = new Photo($waypoint, $filename, $width, $height);
        $waypoint->addPhoto($photo);
        // A new photo changes the waypoint, for clients that sync with updatedSince
        $waypoint->touch();

        $this->entityManager->persist($photo);
        $this->entityManager->flush();

        return $photo;
    }
}
