<?php

declare(strict_types=1);

namespace App\Controller\Tag;

use App\Controller\BaseController;
use App\Entity\Tag;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * Deletes the tag; its waypoints stay.
 */
#[Route('/tags/{id}/delete', name: 'app_tag_delete', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'tag', statusCode: 404)]
final class Delete extends BaseController
{
    public function __invoke(Request $request, Tag $tag, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete-tag-'.$tag->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($tag);
            $entityManager->flush();
            $this->addFlash('success', 'tag.flash.deleted');
        } else {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');
        }

        return $this->redirectToRoute('app_tag_index', [], Response::HTTP_SEE_OTHER);
    }
}
