package ch.swisstopo.monteis.core.modules.visualization.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures where the 3D Tiles tileset (produced client-side - see the frontend's
 * tiling.worker.ts - and uploaded via {@code TileUploadController}) is stored and served from.
 */
@ConfigurationProperties(prefix = "monteis.tiling")
public record TilingProperties(String outputDir) {}
