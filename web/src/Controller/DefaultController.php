<?php

declare(strict_types=1);

namespace App\Controller;

use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Component\ErrorHandler\ErrorRenderer\FileLinkFormatter;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Kernel;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/', name: 'app_default', methods: ['GET'])]
class DefaultController extends BaseController
{
    public function __invoke(
        #[Autowire('%kernel.project_dir%')] string $projectDir,
        #[Autowire(service: 'debug.file_link_formatter')] FileLinkFormatter $fileLinkFormatter,
    ): Response {
        return $this->render('default/index.html.twig', [
            'controller_name' => 'DefaultController',
            'php_version' => PHP_VERSION,
            'symfony_version' => Kernel::VERSION,
            'project_dir' => $projectDir,
            'controller_link' => $fileLinkFormatter->format($projectDir.'/src/Controller/DefaultController.php', 0),
            'template_link' => $fileLinkFormatter->format($projectDir.'/templates/default/index.html.twig', 0),
        ]);
    }
}
