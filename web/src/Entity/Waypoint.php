<?php

declare(strict_types=1);

namespace App\Entity;

use ApiPlatform\Doctrine\Orm\Filter\IriFilter;
use ApiPlatform\Metadata\ApiResource;
use ApiPlatform\Metadata\Delete;
use ApiPlatform\Metadata\Get;
use ApiPlatform\Metadata\GetCollection;
use ApiPlatform\Metadata\Patch;
use ApiPlatform\Metadata\Post;
use ApiPlatform\Metadata\QueryParameter;
use App\Filter\BboxFilter;
use App\Filter\UpdatedSinceFilter;
use App\Repository\WaypointRepository;
use App\Validator\OwnedByUser;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Attribute\Context;
use Symfony\Component\Serializer\Attribute\Groups;
use Symfony\Component\Serializer\Normalizer\DateTimeNormalizer;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * A place in the user's GPS diary.
 */
#[ORM\Entity(repositoryClass: WaypointRepository::class)]
#[ORM\Index(fields: ['latitude', 'longitude'])]
#[ORM\HasLifecycleCallbacks]
#[ApiResource(
    description: "The user's waypoints: a position (WGS 84 latitude and longitude in degrees), a title, a description, the day of the visit (Y-m-d, optional), a category and tags (IRIs of the user's own). createdAt and updatedAt are set by the server.",
    operations: [
        new GetCollection(
            parameters: [
                'bbox' => new QueryParameter(
                    schema: ['type' => 'string', 'example' => '-78.6,-0.3,-78.4,-0.1'],
                    filter: new BboxFilter(),
                    description: "Only waypoints in this area: minLongitude,minLatitude,maxLongitude,maxLatitude (the map's visible bounds).",
                ),
                'category' => new QueryParameter(
                    filter: new IriFilter(),
                    property: 'category',
                    description: 'Only waypoints of this category (IRI).',
                ),
                'tag' => new QueryParameter(
                    filter: new IriFilter(),
                    property: 'tags',
                    description: 'Only waypoints with this tag (IRI).',
                ),
                'updatedSince' => new QueryParameter(
                    schema: ['type' => 'string', 'format' => 'date-time'],
                    filter: new UpdatedSinceFilter(),
                    description: 'Only waypoints changed at or after this time (RFC 3339), for syncing a cache.',
                ),
            ],
        ),
        new Post(),
        new Get(security: "is_granted('OWNER', object)"),
        new Patch(security: "is_granted('OWNER', object)"),
        new Delete(security: "is_granted('OWNER', object)"),
    ],
    normalizationContext: ['groups' => [self::READ]],
    denormalizationContext: ['groups' => [self::WRITE]],
    order: ['visitedAt' => 'DESC', 'id' => 'DESC'],
    paginationClientItemsPerPage: true,
    paginationMaximumItemsPerPage: 1000,
    security: "is_granted('ROLE_USER')",
)]
class Waypoint implements OwnedInterface
{
    use OwnedTrait;

    public const string READ = 'waypoint:read';

    public const string WRITE = 'waypoint:write';

    #[ORM\Id, ORM\GeneratedValue, ORM\Column]
    #[Groups([self::READ])]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Groups([self::READ, self::WRITE])]
    #[Assert\NotBlank(normalizer: 'trim')]
    #[Assert\Length(max: 255)]
    private string $title = '';

    #[ORM\Column(type: Types::TEXT)]
    #[Groups([self::READ, self::WRITE])]
    #[Assert\Length(max: 20000)]
    private string $description = '';

    #[ORM\Column]
    #[Groups([self::READ, self::WRITE])]
    #[Assert\NotNull]
    #[Assert\Range(min: -90, max: 90)]
    private ?float $latitude = null;

    #[ORM\Column]
    #[Groups([self::READ, self::WRITE])]
    #[Assert\NotNull]
    #[Assert\Range(min: -180, max: 180)]
    private ?float $longitude = null;

    /** The day of the visit, which can differ from when the waypoint was saved. */
    #[ORM\Column(type: Types::DATE_IMMUTABLE, nullable: true)]
    #[Groups([self::READ, self::WRITE])]
    #[Context([DateTimeNormalizer::FORMAT_KEY => 'Y-m-d'])]
    private ?\DateTimeImmutable $visitedAt = null;

    #[ORM\ManyToOne]
    #[ORM\JoinColumn(onDelete: 'SET NULL')]
    #[Groups([self::READ, self::WRITE])]
    #[OwnedByUser]
    private ?Category $category = null;

    /**
     * @var Collection<int, Tag>
     */
    #[ORM\ManyToMany(targetEntity: Tag::class)]
    #[ORM\JoinTable(name: 'waypoint_tag')]
    #[ORM\JoinColumn(onDelete: 'CASCADE')]
    #[ORM\InverseJoinColumn(onDelete: 'CASCADE')]
    #[ORM\OrderBy(['name' => \SortDirection::Ascending])]
    #[Groups([self::READ, self::WRITE])]
    #[Assert\All([new OwnedByUser()])]
    private Collection $tags;

    /**
     * Removed with the waypoint through Doctrine, so PhotoFileRemover deletes their files.
     *
     * @var Collection<int, Photo>
     */
    #[ORM\OneToMany(targetEntity: Photo::class, mappedBy: 'waypoint', cascade: ['remove'], orphanRemoval: true)]
    #[ORM\OrderBy(['id' => \SortDirection::Ascending])]
    #[Groups([self::READ])]
    private Collection $photos;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    #[Groups([self::READ])]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    #[Groups([self::READ])]
    private \DateTimeImmutable $updatedAt;

    public function __construct()
    {
        $this->tags = new ArrayCollection();
        $this->photos = new ArrayCollection();
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = $this->createdAt;
    }

    #[ORM\PreUpdate]
    public function touch(): void
    {
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getTitle(): string
    {
        return $this->title;
    }

    public function setTitle(string $title): static
    {
        $this->title = \trim($title);

        return $this;
    }

    public function getDescription(): string
    {
        return $this->description;
    }

    public function setDescription(string $description): static
    {
        $this->description = $description;

        return $this;
    }

    public function getLatitude(): ?float
    {
        return $this->latitude;
    }

    public function setLatitude(float $latitude): static
    {
        $this->latitude = $latitude;

        return $this;
    }

    public function getLongitude(): ?float
    {
        return $this->longitude;
    }

    public function setLongitude(float $longitude): static
    {
        $this->longitude = $longitude;

        return $this;
    }

    public function getVisitedAt(): ?\DateTimeImmutable
    {
        return $this->visitedAt;
    }

    public function setVisitedAt(?\DateTimeImmutable $visitedAt): static
    {
        $this->visitedAt = $visitedAt;

        return $this;
    }

    public function getCategory(): ?Category
    {
        return $this->category;
    }

    public function setCategory(?Category $category): static
    {
        $this->category = $category;

        return $this;
    }

    /**
     * @return Collection<int, Tag>
     */
    public function getTags(): Collection
    {
        return $this->tags;
    }

    public function addTag(Tag $tag): static
    {
        if (!$this->tags->contains($tag)) {
            $this->tags->add($tag);
        }

        return $this;
    }

    public function removeTag(Tag $tag): static
    {
        $this->tags->removeElement($tag);

        return $this;
    }

    /**
     * @return Collection<int, Photo>
     */
    public function getPhotos(): Collection
    {
        return $this->photos;
    }

    public function addPhoto(Photo $photo): static
    {
        if (!$this->photos->contains($photo)) {
            $this->photos->add($photo);
        }

        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function getUpdatedAt(): \DateTimeImmutable
    {
        return $this->updatedAt;
    }
}
