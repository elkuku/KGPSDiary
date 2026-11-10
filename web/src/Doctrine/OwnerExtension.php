<?php

declare(strict_types=1);

namespace App\Doctrine;

use ApiPlatform\Doctrine\Orm\Extension\QueryCollectionExtensionInterface;
use ApiPlatform\Doctrine\Orm\Extension\QueryItemExtensionInterface;
use ApiPlatform\Doctrine\Orm\Util\QueryNameGeneratorInterface;
use ApiPlatform\Metadata\Operation;
use App\Entity\OwnedInterface;
use Doctrine\ORM\QueryBuilder;
use Symfony\Bundle\SecurityBundle\Security;

/**
 * API queries of {@see OwnedInterface} records only find the signed-in user's: other users'
 * records are "not found" (404), also as IRIs in a request body (400).
 */
final readonly class OwnerExtension implements QueryCollectionExtensionInterface, QueryItemExtensionInterface
{
    public function __construct(private Security $security)
    {
    }

    #[\Override]
    public function applyToCollection(QueryBuilder $queryBuilder, QueryNameGeneratorInterface $queryNameGenerator, string $resourceClass, ?Operation $operation = null, array $context = []): void
    {
        $this->restrict($queryBuilder, $queryNameGenerator, $resourceClass);
    }

    #[\Override]
    public function applyToItem(QueryBuilder $queryBuilder, QueryNameGeneratorInterface $queryNameGenerator, string $resourceClass, array $identifiers, ?Operation $operation = null, array $context = []): void
    {
        $this->restrict($queryBuilder, $queryNameGenerator, $resourceClass);
    }

    private function restrict(QueryBuilder $queryBuilder, QueryNameGeneratorInterface $queryNameGenerator, string $resourceClass): void
    {
        if (!\is_a($resourceClass, OwnedInterface::class, true)) {
            return;
        }

        $alias = $queryBuilder->getRootAliases()[0];
        $user = $this->security->getUser();

        if (!$user instanceof \Symfony\Component\Security\Core\User\UserInterface) {
            $queryBuilder->andWhere('1 = 0');

            return;
        }

        $name = $queryNameGenerator->generateParameterName('owner');
        $queryBuilder->andWhere(\sprintf('%s.owner = :%s', $alias, $name))->setParameter($name, $user);
    }
}
