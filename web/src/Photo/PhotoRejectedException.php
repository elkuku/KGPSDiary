<?php

declare(strict_types=1);

namespace App\Photo;

/**
 * An upload that is no acceptable photo; the message is for the user.
 */
final class PhotoRejectedException extends \RuntimeException
{
}
