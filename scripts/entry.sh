#!/bin/sh

if mountpoint -q /host/proc; then
	lvmpath=$(command -v lvm)
	mv "$lvmpath" "${lvmpath}.distro"
	cat <<'EOF' > "$lvmpath"
#!/bin/sh
nsenter --mount=/host/proc/1/ns/mnt -- "$(basename $0)" "$@"
EOF
	chmod +x "$lvmpath"
fi

case $1 in
	startSatellite)
		shift
		/usr/share/linstor-server/bin/Satellite --logs=/var/log/linstor-satellite --config-directory=/etc/linstor --skip-hostname-check "$@"
		;;
	startController)
		shift
		/usr/share/linstor-server/bin/Controller --logs=/var/log/linstor-controller --config-directory=/etc/linstor --rest-bind=0.0.0.0:3370 "$@"
		;;
	*) linstor "$@" ;;
esac
