<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Entity\Waypoint;
use App\Form\WaypointType;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/waypoints/{id}/edit', name: 'app_waypoint_edit', requirements: ['id' => '\d+'], methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'waypoint', statusCode: 404)]
final class Update extends BaseController
{
    public function __invoke(Request $request, Waypoint $waypoint, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(WaypointType::class, $waypoint, ['owner' => $this->getUser()]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            $this->addFlash('success', 'waypoint.flash.updated');

            return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
        }

        return $this->render('waypoint/edit.html.twig', ['waypoint' => $waypoint, 'form' => $form]);
    }
}
