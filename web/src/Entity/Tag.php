<?php

declare(strict_types=1);

namespace App\Entity;

use ApiPlatform\Metadata\ApiResource;
use ApiPlatform\Metadata\Delete;
use ApiPlatform\Metadata\Get;
use ApiPlatform\Metadata\GetCollection;
use ApiPlatform\Metadata\Patch;
use ApiPlatform\Metadata\Post;
use App\Repository\TagRepository;
use App\Validator\UniqueForOwner;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Attribute\Groups;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * Any number of tags per waypoint, for filtering.
 */
#[ORM\Entity(repositoryClass: TagRepository::class)]
#[ORM\UniqueConstraint(fields: ['owner', 'name'])]
#[UniqueForOwner(field: 'name')]
#[ApiResource(
    description: "The user's tags. Names are unique per user. Deleting a tag removes it from its waypoints.",
    operations: [
        new GetCollection(),
        new Post(),
        new Get(security: "is_granted('OWNER', object)"),
        new Patch(security: "is_granted('OWNER', object)"),
        new Delete(security: "is_granted('OWNER', object)"),
    ],
    normalizationContext: ['groups' => [self::READ]],
    denormalizationContext: ['groups' => [self::WRITE]],
    order: ['name' => 'ASC'],
    paginationEnabled: false,
    security: "is_granted('ROLE_USER')",
)]
class Tag implements OwnedInterface
{
    use OwnedTrait;

    public const string READ = 'tag:read';

    public const string WRITE = 'tag:write';

    #[ORM\Id, ORM\GeneratedValue, ORM\Column]
    #[Groups([self::READ, Waypoint::READ])]
    private ?int $id = null;

    #[ORM\Column(length: 50)]
    #[Groups([self::READ, self::WRITE, Waypoint::READ])]
    #[Assert\NotBlank(normalizer: 'trim')]
    #[Assert\Length(max: 50)]
    private string $name = '';

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): static
    {
        $this->name = \trim($name);

        return $this;
    }
}
