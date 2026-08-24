/**
 * Training-deck ingestion: docling PPTX/PDF parsing. Depends inward on {@code domain} and
 * the engine adapter modules (AD-1). Scaffolded like every other module at the source
 * level; its packaging ships only in a separate ingest-time-only image, never the serving
 * image (AD-28). Parsing lands in Epic 9.
 */
package com.luciad.knowledge.ingest.trainings;
