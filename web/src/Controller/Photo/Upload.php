<?php

declare(strict_types=1);

namespace App\Controller\Photo;

use App\Controller\BaseController;
use App\Entity\Waypoint;
use App\Photo\PhotoRejectedException;
use App\Photo\PhotoUploader;
use App\Security\Voter\OwnerVoter;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * Adds the photos chosen on the waypoint page.
 */
#[Route('/waypoints/{id}/photos', name: 'app_photo_upload', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'waypoint', statusCode: 404)]
final class Upload extends BaseController
{
    public function __invoke(Request $request, Waypoint $waypoint, PhotoUploader $uploader): Response
    {
        if (!$this->isCsrfTokenValid('upload-photos-'.$waypoint->getId(), $request->getPayload()->getString('_token'))) {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');

            return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
        }

        /** @var array<UploadedFile|null> $files */
        $files = (array) $request->files->get('photos', []);
        $added = 0;

        foreach (\array_filter($files) as $file) {
            try {
                $uploader->add($waypoint, $file);
                ++$added;
            } catch (PhotoRejectedException $exception) {
                $this->addFlash('danger', \sprintf('%s: %s', $file->getClientOriginalName(), $exception->getMessage()));
            }
        }

        if ($added > 0) {
            $this->addFlash('success', 'photo.flash.added');
        }

        return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
    }
}
