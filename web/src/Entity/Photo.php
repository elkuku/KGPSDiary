<?php

declare(strict_types=1);

namespace App\Entity;

use ApiPlatform\Metadata\ApiProperty;
use ApiPlatform\Metadata\ApiResource;
use ApiPlatform\Metadata\Delete;
use ApiPlatform\Metadata\Get;
use App\Repository\PhotoRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Attribute\Groups;

/**
 * A photo of a waypoint. The files (a full size JPEG and a thumbnail) are in PhotoStorage,
 * outside the web root: only the owner gets them, through /api/photos/{id}/file or the web.
 */
#[ORM\Entity(repositoryClass: PhotoRepository::class)]
#[ApiResource(
    description: 'A photo of a waypoint, uploaded with POST /api/waypoints/{id}/photos. url and thumbnailUrl need the same bearer token; the full image is at most 2048 pixels, the thumbnail 400.',
    operations: [
        new Get(security: "is_granted('OWNER', object)"),
        new Delete(security: "is_granted('OWNER', object)"),
    ],
    normalizationContext: ['groups' => [self::READ]],
    security: "is_granted('ROLE_USER')",
)]
class Photo implements OwnedInterface
{
    use OwnedTrait;

    public const string READ = 'photo:read';

    #[ORM\Id, ORM\GeneratedValue, ORM\Column]
    #[Groups([self::READ, Waypoint::READ])]
    private ?int $id = null;

    #[ORM\Column(type: 'datetimetz_immutable')]
    #[Groups([self::READ, Waypoint::READ])]
    private \DateTimeImmutable $createdAt;

    public function __construct(#[ORM\ManyToOne(inversedBy: 'photos')]
        #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
        private Waypoint $waypoint, /** The stored file's name: a random hex name with .jpg. */
        #[ORM\Column(length: 64, unique: true)]
        private string $filename, #[ORM\Column]
        #[Groups([self::READ, Waypoint::READ])]
        private int $width, #[ORM\Column]
        #[Groups([self::READ, Waypoint::READ])]
        private int $height)
    {
        $this->createdAt = new \DateTimeImmutable();

        if ($this->waypoint->getOwner() instanceof User) {
            $this->setOwner($this->waypoint->getOwner());
        }
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getWaypoint(): Waypoint
    {
        return $this->waypoint;
    }

    public function getFilename(): string
    {
        return $this->filename;
    }

    public function getWidth(): int
    {
        return $this->width;
    }

    public function getHeight(): int
    {
        return $this->height;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    /** The full image, relative to the server; it needs the owner's bearer token. */
    #[Groups([self::READ, Waypoint::READ])]
    #[ApiProperty(writable: false)]
    public function getUrl(): string
    {
        return \sprintf('/api/photos/%d/file', $this->id);
    }

    #[Groups([self::READ, Waypoint::READ])]
    #[ApiProperty(writable: false)]
    public function getThumbnailUrl(): string
    {
        return \sprintf('/api/photos/%d/file?size=thumb', $this->id);
    }
}
