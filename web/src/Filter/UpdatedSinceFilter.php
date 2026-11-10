<?php

declare(strict_types=1);

namespace App\Filter;

use ApiPlatform\Doctrine\Orm\Filter\FilterInterface;
use ApiPlatform\Doctrine\Orm\Util\QueryNameGeneratorInterface;
use ApiPlatform\Metadata\Exception\InvalidArgumentException;
use ApiPlatform\Metadata\Operation;
use ApiPlatform\Metadata\Parameter;
use Doctrine\ORM\QueryBuilder;

/**
 * `?updatedSince=2026-11-10T12:00:00Z`: the records changed at or after this time.
 */
final class UpdatedSinceFilter implements FilterInterface
{
    #[\Override]
    public function apply(QueryBuilder $queryBuilder, QueryNameGeneratorInterface $queryNameGenerator, string $resourceClass, ?Operation $operation = null, array $context = []): void
    {
        $parameter = $context['parameter'] ?? null;
        $value = $parameter instanceof Parameter ? $parameter->getValue() : null;

        if (!\is_string($value) || '' === $value) {
            return;
        }

        try {
            $since = new \DateTimeImmutable($value);
        } catch (\Exception) {
            throw new InvalidArgumentException('updatedSince must be a date and time like 2026-11-10T12:00:00Z.');
        }

        $alias = $queryBuilder->getRootAliases()[0];
        $name = $queryNameGenerator->generateParameterName('updatedSince');

        $queryBuilder
            ->andWhere(\sprintf('%s.updatedAt >= :%s', $alias, $name))
            ->setParameter($name, $since);
    }

    #[\Override]
    public function getDescription(string $resourceClass): array
    {
        return [];
    }
}
