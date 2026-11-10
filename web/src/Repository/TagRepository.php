<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Tag;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Tag>
 */
class TagRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Tag::class);
    }

    /**
     * @return list<Tag> the user's, sorted by name
     */
    public function findByOwner(User $owner): array
    {
        /* @var list<Tag> */
        return $this->createQueryBuilder('e')
            ->where('e.owner = :owner')
            ->setParameter('owner', $owner)
            ->orderBy('e.name', \SortDirection::Ascending)
            ->getQuery()
            ->getResult();
    }
}
