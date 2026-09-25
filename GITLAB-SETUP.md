# Publish the source code

The WispApps project spaces are available at [GitLab](https://gitlab.com/wispapps) and [GitHub](https://github.com/WispApps). Create a repository named `wisp-to-do` in each space. Keep one repository as the primary and mirror/push the same commits to the other so the two copies do not diverge.

Run these commands from the Wisp To Do project folder after a successful build. The Gradle Wrapper files must be included.

```powershell
git init
git add .
git status
git commit -m "Initial Wisp To Do source"
git branch -M main
git remote add origin https://gitlab.com/wispapps/wisp-to-do.git
git push -u origin main
```

To publish the same commit to GitHub as a second remote:

```powershell
git remote add github https://github.com/WispApps/wisp-to-do.git
git push -u github main
```

Do not add `local.properties`, signing keys, app passwords, recovery phrases or build folders to Git.
