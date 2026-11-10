<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Entity\Waypoint;
use App\Form\WaypointType;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * A new waypoint, at `?latitude=…&longitude=…` when the map sends it, visited today.
 */
#[Route('/waypoints/new', name: 'app_waypoint_new', methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
final class Create extends BaseController
{
    public function __invoke(Request $request, EntityManagerInterface $entityManager): Response
    {
        $waypoint = new Waypoint()
            ->setOwner($this->getUser())
            ->setVisitedAt(new \DateTimeImmutable('today'));

        $latitude = $request->query->get('latitude');
        $longitude = $request->query->get('longitude');
        if (\is_numeric($latitude) && \is_numeric($longitude)) {
            $waypoint->setLatitude(\round((float) $latitude, 6))->setLongitude(\round((float) $longitude, 6));
        }

        $form = $this->createForm(WaypointType::class, $waypoint, ['owner' => $this->getUser()]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($waypoint);
            $entityManager->flush();

            $this->addFlash('success', 'waypoint.flash.created');

            return $this->redirectToRoute('app_waypoint_show', ['id' => $waypoint->getId()], Response::HTTP_SEE_OTHER);
        }

        return $this->render('waypoint/new.html.twig', ['form' => $form]);
    }
}
