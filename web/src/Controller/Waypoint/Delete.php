<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Entity\Waypoint;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/waypoints/{id}/delete', name: 'app_waypoint_delete', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'waypoint', statusCode: 404)]
final class Delete extends BaseController
{
    public function __invoke(Request $request, Waypoint $waypoint, EntityManagerInterface $entityManager): Response
    {
        if (!$this->isCsrfTokenValid('delete-waypoint-'.$waypoint->getId(), $request->getPayload()->getString('_token'))) {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');

            return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
        }

        $entityManager->remove($waypoint);
        $entityManager->flush();

        $this->addFlash('success', 'waypoint.flash.deleted');

        return $this->redirectToRoute('app_waypoint_map', [], Response::HTTP_SEE_OTHER);
    }
}
