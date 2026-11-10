<?php

declare(strict_types=1);

namespace App\Controller;

use App\Entity\User;
use App\Repository\WaypointRepository;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\CurrentUser;

#[Route('/', name: 'app_default', methods: ['GET'])]
class DefaultController extends BaseController
{
    public function __invoke(WaypointRepository $waypointRepository, #[CurrentUser] ?User $user = null): Response
    {
        return $this->render('default/index.html.twig', [
            'waypoint_count' => $user instanceof User ? $waypointRepository->count(['owner' => $user]) : 0,
        ]);
    }
}
