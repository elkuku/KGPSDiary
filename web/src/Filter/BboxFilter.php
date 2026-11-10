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
 * `?bbox=minLongitude,minLatitude,maxLongitude,maxLatitude`: the records whose latitude and
 * longitude are in this area. An area crossing the antimeridian has minLongitude > maxLongitude.
 */
final class BboxFilter implements FilterInterface
{
    #[\Override]
    public function apply(QueryBuilder $queryBuilder, QueryNameGeneratorInterface $queryNameGenerator, string $resourceClass, ?Operation $operation = null, array $context = []): void
    {
        $parameter = $context['parameter'] ?? null;
        $value = $parameter instanceof Parameter ? $parameter->getValue() : null;

        if (!\is_string($value) || '' === $value) {
            return;
        }

        [$minLongitude, $minLatitude, $maxLongitude, $maxLatitude] = self::parse($value);

        $alias = $queryBuilder->getRootAliases()[0];
        $names = \array_map($queryNameGenerator->generateParameterName(...), ['minLat', 'maxLat', 'minLon', 'maxLon']);

        $queryBuilder
            ->andWhere(\sprintf('%s.latitude BETWEEN :%s AND :%s', $alias, $names[0], $names[1]))
            ->andWhere(\sprintf(
                $minLongitude <= $maxLongitude ? '%s.longitude BETWEEN :%s AND :%s' : '(%1$s.longitude >= :%2$s OR %1$s.longitude <= :%3$s)',
                $alias,
                $names[2],
                $names[3],
            ))
            ->setParameter($names[0], $minLatitude)
            ->setParameter($names[1], $maxLatitude)
            ->setParameter($names[2], $minLongitude)
            ->setParameter($names[3], $maxLongitude);
    }

    /**
     * @return array{float, float, float, float}
     */
    public static function parse(string $value): array
    {
        $parts = \explode(',', $value);

        if (4 !== \count($parts) || \array_filter($parts, static fn (string $part): bool => !\is_numeric(\trim($part)))) {
            throw new InvalidArgumentException('bbox must be minLongitude,minLatitude,maxLongitude,maxLatitude.');
        }

        [$minLongitude, $minLatitude, $maxLongitude, $maxLatitude] = \array_map(\floatval(...), $parts);

        if ($minLatitude > $maxLatitude || \abs($minLatitude) > 90 || \abs($maxLatitude) > 90 || \abs($minLongitude) > 180 || \abs($maxLongitude) > 180) {
            throw new InvalidArgumentException('bbox is out of range.');
        }

        return [$minLongitude, $minLatitude, $maxLongitude, $maxLatitude];
    }

    #[\Override]
    public function getDescription(string $resourceClass): array
    {
        return [];
    }
}
