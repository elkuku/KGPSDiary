<?php

declare(strict_types=1);

namespace App\Controller\Category;

use App\Controller\BaseController;
use App\Entity\Category;
use App\Security\Voter\OwnerVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * Deletes the category; its waypoints stay.
 */
#[Route('/categories/{id}/delete', name: 'app_category_delete', requirements: ['id' => '\d+'], methods: ['POST'])]
#[IsGranted('ROLE_USER')]
#[IsGranted(OwnerVoter::OWNER, subject: 'category', statusCode: 404)]
final class Delete extends BaseController
{
    public function __invoke(Request $request, Category $category, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete-category-'.$category->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($category);
            $entityManager->flush();
            $this->addFlash('success', 'category.flash.deleted');
        } else {
            $this->addFlash('danger', 'waypoint.flash.invalid_token');
        }

        return $this->redirectToRoute('app_category_index', [], Response::HTTP_SEE_OTHER);
    }
}
