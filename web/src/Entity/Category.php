<?php

declare(strict_types=1);

namespace App\Entity;

use ApiPlatform\Metadata\ApiResource;
use ApiPlatform\Metadata\Delete;
use ApiPlatform\Metadata\Get;
use ApiPlatform\Metadata\GetCollection;
use ApiPlatform\Metadata\Patch;
use ApiPlatform\Metadata\Post;
use App\Repository\CategoryRepository;
use App\Validator\UniqueForOwner;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Attribute\Groups;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * A waypoint has at most one category; it sets the marker's color and icon.
 */
#[ORM\Entity(repositoryClass: CategoryRepository::class)]
#[ORM\UniqueConstraint(fields: ['owner', 'name'])]
#[UniqueForOwner(field: 'name')]
#[ApiResource(
    description: "The user's categories. A waypoint has at most one; it sets the marker's color (#rrggbb) and icon (a Material Symbols name). Names are unique per user. Deleting a category removes it from its waypoints.",
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
class Category implements OwnedInterface
{
    use OwnedTrait;

    public const string READ = 'category:read';

    public const string WRITE = 'category:write';

    public const string DEFAULT_COLOR = '#2e7d32';

    #[ORM\Id, ORM\GeneratedValue, ORM\Column]
    #[Groups([self::READ, Waypoint::READ])]
    private ?int $id = null;

    #[ORM\Column(length: 50)]
    #[Groups([self::READ, self::WRITE, Waypoint::READ])]
    #[Assert\NotBlank(normalizer: 'trim')]
    #[Assert\Length(max: 50)]
    private string $name = '';

    #[ORM\Column(length: 7)]
    #[Groups([self::READ, self::WRITE, Waypoint::READ])]
    #[Assert\NotBlank]
    #[Assert\CssColor(formats: Assert\CssColor::HEX_LONG)]
    private string $color = self::DEFAULT_COLOR;

    #[ORM\Column(length: 50, nullable: true)]
    #[Groups([self::READ, self::WRITE, Waypoint::READ])]
    #[Assert\Length(max: 50)]
    #[Assert\Regex(pattern: '/^[a-z0-9_]+$/', message: 'Use a Material Symbols name like "hiking".')]
    private ?string $icon = null;

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

    public function getColor(): string
    {
        return $this->color;
    }

    public function setColor(string $color): static
    {
        $this->color = \strtolower($color);

        return $this;
    }

    public function getIcon(): ?string
    {
        return $this->icon;
    }

    public function setIcon(?string $icon): static
    {
        $this->icon = $icon;

        return $this;
    }
}
