<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Controller\BaseController;
use App\Entity\Photo;
use App\Photo\PhotoFileResponse;
use App\Security\Voter\OwnerVoter;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * GET /api/photos/{id}/file: the user's photo as JPEG; `?size=thumb` for the thumbnail.
 */
#[Route('/api/photos/{id}/file', name: 'api_photo_file', requirements: ['id' => '\d+'], methods: ['GET'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'photo', statusCode: 404)]
final class PhotoFile extends BaseController
{
    public function __invoke(Request $request, Photo $photo, PhotoFileResponse $response): BinaryFileResponse
    {
        return $response->create($photo, $request->query->getString('size'));
    }
}
