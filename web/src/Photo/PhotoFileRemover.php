<?php

declare(strict_types=1);

namespace App\Photo;

use App\Entity\Photo;
use Doctrine\Bundle\DoctrineBundle\Attribute\AsEntityListener;
use Doctrine\ORM\Events;

/**
 * A deleted photo changes its waypoint (for clients that sync with updatedSince), and its files
 * go with it, also when its waypoint is deleted (cascade remove).
 */
#[AsEntityListener(event: Events::preRemove, method: 'preRemove', entity: Photo::class)]
#[AsEntityListener(event: Events::postRemove, method: 'postRemove', entity: Photo::class)]
final readonly class PhotoFileRemover
{
    public function __construct(private PhotoStorage $storage)
    {
    }

    public function preRemove(Photo $photo): void
    {
        $photo->getWaypoint()->touch();
    }

    public function postRemove(Photo $photo): void
    {
        $this->storage->remove($photo->getFilename());
    }
}
