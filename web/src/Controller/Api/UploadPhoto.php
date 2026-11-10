<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Controller\BaseController;
use App\Entity\Photo;
use App\Entity\Waypoint;
use App\Photo\PhotoRejectedException;
use App\Photo\PhotoUploader;
use App\Security\Voter\OwnerVoter;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * POST /api/waypoints/{id}/photos: adds the image in the multipart field "file" (JPEG, PNG,
 * WebP or AVIF, up to 20 MB) to the user's waypoint.
 */
#[Route('/api/waypoints/{id}/photos', name: 'api_waypoint_photo_upload', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'waypoint', statusCode: 404)]
final class UploadPhoto extends BaseController
{
    public function __invoke(Request $request, Waypoint $waypoint, PhotoUploader $uploader): JsonResponse
    {
        try {
            $photo = $uploader->add($waypoint, $request->files->get('file'));
        } catch (PhotoRejectedException $photoRejectedException) {
            return new JsonResponse(
                [
                    'type' => '/errors/422',
                    'title' => 'An error occurred',
                    'status' => Response::HTTP_UNPROCESSABLE_ENTITY,
                    'detail' => $photoRejectedException->getMessage(),
                ],
                Response::HTTP_UNPROCESSABLE_ENTITY,
                ['Content-Type' => 'application/problem+json'],
            );
        }

        return $this->json($photo, Response::HTTP_CREATED, [], ['groups' => [Photo::READ]]);
    }
}
