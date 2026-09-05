# Standalone asset extraction

Input: [user-provided reference board](../design-demos/assets/riftdeck-brand-board.png).

Method: built-in `image_gen`, one edit call for each asset. Outputs are image-model extractions from the reference. PNG outputs were converted losslessly to WebP; decoded pixels were compared to verify that format conversion preserved the generated pixels.

## Emblem

Output: [riftdeck_emblem.webp](../app/src/main/res/drawable-nodpi/riftdeck_emblem.webp), 1406×1119.

Prompt:

> Extract only the existing colored handheld-and-rift emblem from the TOP LEFT of this provided RiftDeck brand board as a standalone production UI asset. This is an asset extraction, NOT a redesign. Preserve its exact silhouette, original neon yellow left edge, cyan right edge, dark controller halves, yellow plus D-pad, four cyan buttons, jagged vertical portal, tiny pixel fragments and interior grid. Exclude the RiftDeck wordmark, all board captions, all dividers, all other logo variants, all square app-icon frames. The source region is x=80,y=148,width=430,height=342 in a 1254x1254 board. Output ONLY that emblem tightly framed with minimal black margin on a pure black background, aspect ratio 430:342. Do not add lettering or a presentation board. Do not restyle, embellish, sharpen into new shapes, or add extra effects. It must work as the same logo in the app rail.

## Wordmark

Output: [riftdeck_wordmark.webp](../app/src/main/res/drawable-nodpi/riftdeck_wordmark.webp), 1933×814.

Prompt:

> Extract ONLY the existing top horizontal RiftDeck WORDMARK from the supplied brand board as a standalone production app-header asset. Text must be exactly 'RiftDeck', capital R and D, neon yellow 'Rift' and cyan 'Deck', with the same distinctive angular italic letters, the original jagged horizontal cut through the word, and original letter shapes. This is extraction, NOT a logo redesign. Source wordmark region is x=528,y=249,width=664,height=140 in this 1254x1254 board. Exclude the controller emblem, all slogans/taglines, all arrows, all captions, all divider lines, and the lower monochrome version. Output a tightly framed, wide image of only that wordmark, very minimal black padding, pure black background, approximately 4.74:1 aspect ratio. Preserve the original typography and colors. Do not add any new graphics or presentation board. The entire requested wordmark must be within the image, with neither edge cropped.
