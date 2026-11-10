<?php

declare(strict_types=1);

namespace App\Controller\Photo;

use App\Controller\BaseController;
use App\Entity\Photo;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/photos/{id}/delete', name: 'app_photo_delete', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'photo', statusCode: 404)]
final class Delete extends BaseController
{
    public function __invoke(Request $request, Photo $photo, EntityManagerInterface $entityManager): Response
    {
        $waypoint = $photo->getWaypoint();

        if ($this->isCsrfTokenValid('delete-photo-'.$photo->getId(), $request->getPayload()->getString('_token'))) {
            $waypoint->getPhotos()->removeElement($photo);
            $entityManager->remove($photo);
            $entityManager->flush();
            $this->addFlash('success', 'photo.flash.deleted');
        } else {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');
        }

        return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
    }
}
