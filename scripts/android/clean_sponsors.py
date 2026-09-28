#!/usr/bin/env python3
"""
Clean third-party sponsor assets and funding configurations.
"""
import os
import glob

def clean_assets():
    # Remove all sponsor image files in assets/ root (keep assets/logo/)
    for ext in ('*.png', '*.jpg', '*.jpeg'):
        for img in glob.glob(os.path.join('assets', ext)):
            try:
                os.remove(img)
                print(f"Removed sponsor asset: {img}")
            except OSError:
                pass

    funding = os.path.join('.github', 'FUNDING.yml')
    if os.path.exists(funding):
        os.remove(funding)
        print("Removed .github/FUNDING.yml")

    issue_template = os.path.join('.github', 'ISSUE_TEMPLATE')
    if os.path.exists(issue_template):
        import shutil
        shutil.rmtree(issue_template, ignore_errors=True)
        print("Removed .github/ISSUE_TEMPLATE")

    # Clean leftover Japanese readme if recreated by upstream merge
    if os.path.exists('README_JA.md'):
        os.remove('README_JA.md')

if __name__ == '__main__':
    clean_assets()
