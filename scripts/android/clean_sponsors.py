#!/usr/bin/env python3
"""
Clean third-party sponsor assets, funding configurations, desktop files, and examples.
"""
import os
import glob
import shutil

def clean_assets():
    # Remove all sponsor image files in assets/ root (keep assets/logo/)
    for ext in ('*.png', '*.jpg', '*.jpeg'):
        for img in glob.glob(os.path.join('assets', ext)):
            try:
                os.remove(img)
                print(f"Removed sponsor asset: {img}")
            except OSError:
                pass

    for f in (
        os.path.join('.github', 'FUNDING.yml'),
        'README_JA.md',
        'Dockerfile',
        '.dockerignore',
        'docker-compose.yml',
        'docker-compose.cluster.yml',
        'docker-build.sh',
        'docker-build.ps1',
        '.env.cluster.example'
    ):
        if os.path.exists(f):
            try:
                os.remove(f)
                print(f"Removed: {f}")
            except OSError:
                pass

    for d in (
        os.path.join('.github', 'ISSUE_TEMPLATE'),
        'examples'
    ):
        if os.path.exists(d):
            shutil.rmtree(d, ignore_errors=True)
            print(f"Removed directory: {d}")

    # Remove desktop markdown docs
    for doc in glob.glob(os.path.join('docs', 'sdk-*.md')):
        try:
            os.remove(doc)
        except OSError:
            pass

if __name__ == '__main__':
    clean_assets()
