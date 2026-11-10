<?php

declare(strict_types=1);

namespace App\Controller\Category;

use App\Controller\BaseController;
use App\Entity\Category;
use App\Form\CategoryType;
use App\Repository\CategoryRepository;
use App\Repository\WaypointRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * The user's categories, and a form for a new one.
 */
#[Route('/categories', name: 'app_category_index', methods: ['GET', 'POST'])]
#[IsGranted('ROLE_USER')]
final class Index extends BaseController
{
    public function __invoke(
        Request $request,
        CategoryRepository $categoryRepository,
        WaypointRepository $waypointRepository,
        EntityManagerInterface $entityManager,
    ): Response {
        $category = new Category()->setOwner($this->getUser());
        $form = $this->createForm(CategoryType::class, $category);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($category);
            $entityManager->flush();

            $this->addFlash('success', 'category.flash.created');

            return $this->redirectToRoute('app_category_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('category/index.html.twig', [
            'categories' => $categoryRepository->findByOwner($this->getUser()),
            'counts' => $waypointRepository->countByCategory($this->getUser()),
            'form' => $form,
        ]);
    }
}
