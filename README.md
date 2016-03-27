# Anodyne Mapper

Utility for creating maps for [Anodyne](http://www.anodynegame.com/).

## How to use it

This project requires Java (v1.7).
It builds in Eclipse (v4.5).

Once you have this utility running, run Anodyne in another window.
Configure Anodyne to run with 1x scaling in windowed mode.
This will make the game appear very, very tiny (160x180 pixels, to be exact).
Click "Find Window", and you should see a yellow boarder appear around (or possibly behind) the Anodyne window,
and you should start getting a video feed of the 160x160-pixel main area of the game.

When you walk Young around, the graph of red/blue/green lines should update to show you where the utility thinks you are.
If the graph isn't updating, and you don't see a yellow rectangle in front of Young in the video feed,
it means the sprite recognition doesn't work yet in this area.
Many areas have subtle lighting effects that trick this utility's image processing code so that it doesn't find Young properly.
If you're having trouble with this, I recommend going to the [Woods](http://anodyne.wikia.com/wiki/Woods) to make sure the program can detect you at all.

This utility requires decent performance in order to function.
The FPS counter should get 10-30 fps.
If you're getting less than 10 fps, this program probably won't work for you. sorry :(

Now click Start Recording, and trying moving past the edge of a screen to cause a screen scroll.
When the next screen comes into view, stand still.
*Standing still after a screen transition is how you tell this utility to record the screen.*
After recording a screenshot, it should show up in the large map area of this utility, and it should have a yellow overlay.
The yellow overlay means the screenshot is incomplete, because Young's sprite is in it.
Simply move out of the way (but not back through the screen transition), and you should see Young's sprite disappear,
and the screenshot should turn green.
When you move out of the way, this utility keeps recording the pixels on the floor where you're walking away from,
so don't let an enemy or a swapped tile corrupt the recording of that spot while you're walking away.

You should be able to repeat this process to record several screens of the game,
and this utility will automatically pay attention to which direction you're screen scrolling in order to position the screenshots relative to each other to build a coherent map.
If this isn't working, then this utility probably needs work.

There are many cases when this utility can lose track of which map grid you're in, and may even start recording bad data into the wrong cell.
(This will almost surely happen if you enter the super glitchy zone outside the bounds of a map.)
If this happens, right-click on a tile on the map and use "I am here" to correct your position and "Delete Tile" as appropriate.

## Limitations

This utility knows the 12 walking sprites for being on the groun.
There are many more sprites this utility doesn't recognize, such as jumping, attacking, sinking, falling, etc.

Many areas (such as [GO](http://anodyne.wikia.com/wiki/GO)) have lighting effects that change the precise values of the pixels in your sprite, such as making the pixels slightly more yellow, or darker, or even black-and-white.
This utility does not yet have a "fuzzy matching" feature to compensate for these lighting effects.
Currently, this utility is useless in those areas.

Some areas have completely different sprites, such as the [8-Bit Dungeon](http://anodyne.wikia.com/wiki/8-Bit_Dungeon).
This utility doesn't know how to look for those sprites.

Some areas have sporadic particle effects, such as the [Windmill](http://anodyne.wikia.com/wiki/Windmill) or the background for the [Nexus](http://anodyne.wikia.com/wiki/Nexus).
This utility should probably smooth out those effects so that there aren't obvious seems between screenshots where the effects don't line up.
Some ares have a shaky-cam effect, such as the very beginning tutorial.
This utility should probably smooth out that effect as well.

The basic approach of this utility is to use screenshots of the actual game while it's running.
This strategy is not very well suited for areas that contain a darkness effect that causes limited visibility
([Archives](http://anodyne.wikia.com/wiki/DRAWER) and the backdoor to the [Temple](http://anodyne.wikia.com/wiki/Seeing_One_Dungeon) for example).
I don't have a plan to solve those usecases.

## Examples

 * http://anodyne.wikia.com/wiki/File:Woods_map.png
