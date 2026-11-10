<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Category;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Category>
 */
class CategoryRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Category::class);
    }

    /**
     * @return list<Category> the user's, sorted by name
     */
    public function findByOwner(User $owner): array
    {
        /* @var list<Category> */
        return $this->createQueryBuilder('e')
            ->where('e.owner = :owner')
            ->setParameter('owner', $owner)
            ->orderBy('e.name', \SortDirection::Ascending)
            ->getQuery()
            ->getResult();
    }
}
