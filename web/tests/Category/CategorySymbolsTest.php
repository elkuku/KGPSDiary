<?php

declare(strict_types=1);

namespace App\Tests\Category;

use App\Category\CategorySymbols;
use PHPUnit\Framework\TestCase;

class CategorySymbolsTest extends TestCase
{
    public function testNamesAreMaterialSymbolsNames(): void
    {
        foreach (CategorySymbols::PATHS as $name => $path) {
            self::assertMatchesRegularExpression('/^[a-z0-9_]{1,50}$/', $name);
            self::assertMatchesRegularExpression('/^M[0-9MmLlHhVvCcSsQqTtAaZz,.\s-]+$/', $path, $name);
        }

        self::assertSame('museum', CategorySymbols::names()[14]);
        self::assertNull(CategorySymbols::path('hiking'));
        self::assertNull(CategorySymbols::path(null));
    }

    /**
     * The app draws the same symbols: res/drawable/sym_<name>.xml with the same path.
     */
    public function testTheAppHasTheSameSymbols(): void
    {
        $drawables = \dirname(__DIR__, 3).'/app/app/src/main/res/drawable';
        if (!\is_dir($drawables)) {
            self::markTestSkipped('The app is not checked out next to the web app.');
        }

        $app = [];
        foreach (\glob($drawables.'/sym_*.xml') ?: [] as $file) {
            \preg_match('/android:pathData="([^"]+)"/', (string) \file_get_contents($file), $match);
            $app[\substr(\basename($file, '.xml'), 4)] = $match[1] ?? '';
        }

        \ksort($app);
        $web = CategorySymbols::PATHS;
        \ksort($web);

        self::assertSame($web, $app);
    }
}
