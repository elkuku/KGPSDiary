<?php

declare(strict_types=1);

namespace App\Controller\Waypoint;

use App\Controller\BaseController;
use App\Entity\Waypoint;
use App\Security\Voter\OwnerVoter;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/waypoints/{id}', name: 'app_waypoint_show', requirements: ['id' => '\d+'], methods: ['GET'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'waypoint', statusCode: 404)]
final class Read extends BaseController
{
    public function __invoke(Waypoint $waypoint): Response
    {
        return $this->render('waypoint/show.html.twig', ['waypoint' => $waypoint]);
    }
}
