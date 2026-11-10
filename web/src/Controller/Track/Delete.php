<?php

declare(strict_types=1);

namespace App\Controller\Track;

use App\Controller\BaseController;
use App\Entity\Track;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/tracks/{id}/delete', name: 'app_track_delete', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'track', statusCode: 404)]
final class Delete extends BaseController
{
    public function __invoke(Request $request, Track $track, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete-track-'.$track->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($track);
            $entityManager->flush();
            $this->addFlash('success', 'track.flash.deleted');
        } else {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');
        }

        return $this->redirectToRoute('app_track_index', [], Response::HTTP_SEE_OTHER);
    }
}
