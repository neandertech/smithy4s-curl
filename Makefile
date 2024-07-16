check-docs:
	# scala-cli compile README.md *.scala 
	echo "No supported yet"

tests:
	cs launch sn-vcpkg --contrib -- scala-cli curl s2n openssl zlib --rename curl=libcurl -- test .

publish-snapshot:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	scala-cli publish . -S 3.3.3 --signer none

publish:
	scala-cli config publish.credentials s01.oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
	./.github/workflows/import-gpg.sh
	scala-cli publish . -S 3.3.3 --signer gpg --gpg-key 15A7215B6CD4016A

code-check:
	scala-cli fmt *.scala test/*.scala --check

run-example:
	# scala-cli run README.md . -M helloWorld
	echo "No supported yet"

pre-ci:
	scala-cli fmt *.scala test/*.scala

smithy4s:
	cd test && \
		rm -rf httpbin && \
		cs launch smithy4s --contrib -- generate httpbin.smithy --skip resource --skip openapi
