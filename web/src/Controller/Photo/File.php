<?php

declare(strict_types=1);

namespace App\Controller\Photo;

use App\Controller\BaseController;
use App\Entity\Photo;
use App\Photo\PhotoFileResponse;
use App\Photo\PhotoStorage;
use App\Security\Voter\OwnerVoter;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * A photo for the web pages (signed in with the session).
 */
#[Route('/photos/{id}', name: 'app_photo_file', requirements: ['id' => '\d+'], defaults: ['size' => PhotoStorage::FULL], methods: ['GET'])]
#[Route('/photos/{id}/thumb', name: 'app_photo_thumb', requirements: ['id' => '\d+'], defaults: ['size' => PhotoStorage::THUMB], methods: ['GET'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'photo', statusCode: 404)]
final class File extends BaseController
{
    public function __invoke(Photo $photo, string $size, PhotoFileResponse $response): BinaryFileResponse
    {
        return $response->create($photo, $size);
    }
}
