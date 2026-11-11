<?php

declare(strict_types=1);

namespace App\Twig;

use App\Category\CategorySymbols;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

/**
 * `category_symbol(name)`: a category's symbol as inline SVG (nothing for none or an unknown one);
 * `category_symbol_paths()`: all symbols' SVG paths, for the map.
 */
final class CategorySymbolExtension extends AbstractExtension
{
    #[\Override]
    public function getFunctions(): array
    {
        return [
            new TwigFunction('category_symbol', $this->symbol(...), ['is_safe' => ['html']]),
            new TwigFunction('category_symbol_paths', static fn (): array => CategorySymbols::PATHS),
        ];
    }

    public function symbol(?string $name, int $size = 20, string $class = ''): string
    {
        $path = CategorySymbols::path($name);
        if (null === $path) {
            return '';
        }

        return \sprintf(
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="%d" height="%d" fill="currentColor" class="%s" aria-hidden="true"><path d="%s"/></svg>',
            $size,
            $size,
            \htmlspecialchars($class),
            $path,
        );
    }
}
