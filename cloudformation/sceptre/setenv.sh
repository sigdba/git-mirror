#!/bin/bash

[ -d ~/.c9 ] || export AWS_PROFILE=sig

die () {
	echo "ERROR: $*"
}

case $(basename $SHELL) in
	zsh)
		SCRIPT_DIR=$(dirname $0:A)
		;;

	*)
		SCRIPT_DIR=$(cd $(dirname ${BASH_ARGV[0]}); pwd)
		;;
esac

if [ -z "$VENV_BASE" ]; then
	export VENV_BASE="${HOME}/.venvs"
fi

[ -d "$VENV_BASE" ] || mkdir -p "$VENV_BASE"

VENV=$VENV_BASE/git_mirror_venv

unalias python 2>/dev/null

if [ ! -d "$VENV" ]; then
	echo "Creating virtualenv..."
	virtualenv $VENV || die "virtualenv error"


	. $VENV/bin/activate ||die "activate error"
	pip install --upgrade pip
	pip install -Ur $SCRIPT_DIR/requirements.txt --no-cache-dir
else
  . $VENV/bin/activate ||die "activate error"
fi
