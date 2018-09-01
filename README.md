# Skript Hub Docs Tool

Skript Hub Docs Tools is a plugin for Spigot servers to help Skript Addon Developers autogenerate their documentation. This tool offers a one command approach for plugins using Skript's Documentation Annotations to get all their documenatation into a single JSON file. This file can then be uploaded to Skript Hub or used in other tools so all Skripters can view and use the docs.

## Prerequisites

1. A Spigot server 
2. Skript (version dev37 or greater)
3. Your Skript Addon with all the necessary supporting plugins to enable all syntax elements


## How to use

1. Download the Skript Hub Docs Tool from [here](https://github.com/SkriptHub/SkriptHubDocsTool/releases) and place it into your development servers ```/plugin/``` folder. **DO NOT USE THIS PLUGIN IN PRODUCTION!**
2. Run your server.
3. Either in console or in game run the command ```/gendocs```.
4. Verify from the output dialog that there were not any errors/id collisions for your addon that could not be merged successfully. You might need manually check these in the output file.
5. The generated JSON files can be found in the ```\plugins\SkriptHubDocsTool\documentation``` folder.
6. (Optional) Copy the contents of you addons JSON file and paste it into the Skript Hub JSON import tool and submit the docs. Skript Hub will automatically update all your public documentation.

## Permissions

| Permission | Description |
|------------|-------------|
| docstool.command.gendocs | Can use /gendocs  |
    

## Trouble Shooting

### How do I use the Skript Documentation Annotations?

A tutorial can be found [here](https://skripthub.net/tutorials/11).

### How does auto merge work?

Auto merge will try to resolve an ID conflict. If two classes with the same name have the same title and same description but a different syntax pattern, the syntax pattern will be merged to create a single syntax element with both patterns in the JSON file.

### Help I got an ID conflict and the auto merge failed!

ID's are generated based on the class name that a syntax is registered from. An ID collision will happen if two classes with the same name are registered. 

Some addons will have the same class name in different packages, if you do this you can either change the class name or use the ```@DocumentationID("New ID")``` annotation from Skript.

When there is a 3-way conflict a successful merge might show up as a possible failure, make sure to check the final outputted JSON file to make sure that the conflict was resolved. Trying to upload a JSON file to Skript Hub with an ID conflicts will result in an error.

## Credits

This tool is based off [TuSKe's](https://github.com/Tuke-Nuke/TuSKe) documentation generation tools with a few changes and improvements. Most of the base techniques/code is from [TuSke](https://github.com/Tuke-Nuke/TuSKe), so massive thanks to [Tuke-Nuke](https://github.com/Tuke-Nuke)!
