# lein-describe

A Leiningen plugin for describing project information.

## Usage

Put `[describe "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile.

## Example

	$ cd /path/to/clojure/project
    $ lein describe
	...
	Dependency: [org.clojure/clojurescript "0.0-2156"]
	Description: ClojureScript compiler and core runtime library.
	URL: https://github.com/clojure/clojurescript
	License(s): Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
	Dependencies: [com.google.javascript/closure-compiler "v20131014"]
				  [org.clojure/google-closure-library "0.0-20130212-95c19e7f0f5f"]
				  [org.clojure/data.json "0.2.3"]
				  [org.mozilla/rhino "1.7R4"]
				  [org.clojure/tools.reader "0.8.3"]

	Dependency: [com.cemerick/clojurescript.test "0.2.2"]
	Description: Port of clojure.test targeting ClojureScript.
	URL: http://github.com/cemerick/clojurescript.test
	License(s): Eclipse Public License (http://www.eclipse.org/legal/epl-v10.html)
	Dependencies: [org.clojure/clojure "1.5.1"]
				  [org.clojure/clojurescript "0.0-1934"]
				  [org.clojure/tools.nrepl "0.2.3"]
				  [clojure-complete "0.2.3"]
	...
