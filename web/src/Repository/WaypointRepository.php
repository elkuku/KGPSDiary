<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\User;
use App\Entity\Waypoint;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Waypoint>
 */
class WaypointRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Waypoint::class);
    }

    /**
     * The user's waypoints with their category, tags and photos, newest visit first.
     *
     * @return list<Waypoint>
     */
    public function findByOwnerNewestFirst(User $owner): array
    {
        /* @var list<Waypoint> */
        return $this->createQueryBuilder('w')
            ->addSelect('c', 't', 'p')
            ->leftJoin('w.category', 'c')
            ->leftJoin('w.tags', 't')
            ->leftJoin('w.photos', 'p')
            ->where('w.owner = :owner')
            ->setParameter('owner', $owner)
            ->orderBy('w.visitedAt', \SortDirection::Descending)
            ->addOrderBy('w.id', \SortDirection::Descending)
            ->getQuery()
            ->getResult();
    }

    /**
     * @return array<int, int> the number of the user's waypoints per category ID
     */
    public function countByCategory(User $owner): array
    {
        /** @var list<array{id: int, waypoints: int}> $rows */
        $rows = $this->createQueryBuilder('w')
            ->select('IDENTITY(w.category) AS id, COUNT(w.id) AS waypoints')
            ->where('w.owner = :owner')
            ->andWhere('w.category IS NOT NULL')
            ->groupBy('w.category')
            ->setParameter('owner', $owner)
            ->getQuery()
            ->getArrayResult();

        return \array_column($rows, 'waypoints', 'id');
    }

    /**
     * @return array<int, int> the number of the user's waypoints per tag ID
     */
    public function countByTag(User $owner): array
    {
        /** @var list<array{id: int, waypoints: int}> $rows */
        $rows = $this->createQueryBuilder('w')
            ->select('t.id AS id, COUNT(w.id) AS waypoints')
            ->join('w.tags', 't')
            ->where('w.owner = :owner')
            ->groupBy('t.id')
            ->setParameter('owner', $owner)
            ->getQuery()
            ->getArrayResult();

        return \array_column($rows, 'waypoints', 'id');
    }
}
