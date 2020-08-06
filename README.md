### Files you need to add

`prefs.txt`: This file stores the path to the Stencyl workspace, as well as some persistent GUI data after the application has been run.

```
sw.workspace=C:\dev\stencylworks\
```

`repositories.yml`: This file holds the connection information needed to save files to your repository.

```yaml
repositories:
 - url: http://www.polydes.com/repo
   sftp: 127.0.0.1
   username: ____
   password: ____
   root: /var/www/html/repo
```

`sources.yml`: This file points to the location of extension source files on this computer, to enable easy building.

```yaml
repositories:
 - url: http://www.polydes.com/repo
   engine:
     com.polydes.datastruct: C:\dev\polydes\structures\engine
     com.polydes.dialog: C:\dev\polydes\dialog\engine
   toolset:
     com.polydes.datastruct: C:\dev\polydes\structures
     com.polydes.dialog: C:\dev\polydes\dialog
```