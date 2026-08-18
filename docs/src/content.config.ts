import { defineCollection, z } from 'astro:content';

const schemaCollection = defineCollection({
  schema: z.object({
    title: z.string(),
    type: z.string(),
    properties: z.record(z.any()),
    required: z.array(z.string())
  })
});

export const collections = {
  schemas: schemaCollection
};
