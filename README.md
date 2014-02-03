# lein-describe

Have you ever be involved in a Clojure project and wondered "how
are these dependencies used?" or "what do these plugins do?". This
plugin can help you answer those questions.

`lein-describe` provides detailed information about Clojure project
dependencies and plugins. With a single command you can get a glance
at dependency descriptions, licenses, and more.

## Usage

Put `[lein-describe "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` [profile][leiningen-profiles].

## Example

	$ cd /path/to/clojure/project
    $ lein describe
	...
	PROJECT DEPENDENCIES:
	------------------------------------------------------------------------
	Dependency: [com.cemerick/piggieback "0.1.2"]
	Description: Adding support for running ClojureScript REPLs over nREPL.
	URL: http://github.com/cemerick/piggieback
	License(s): Eclipse Public License (http://www.eclipse.org/legal/epl-v10.html)
	Dependencies: [org.clojure/clojure "1.5.1"]
				  [org.clojure/tools.nrepl "0.2.3"]
				  [org.clojure/clojurescript "0.0-2014"]
				  [clojure-complete "0.2.3"]

	...

	PLUGIN DEPENDENCIES:
	------------------------------------------------------------------------
	Dependency: [com.cemerick/austin "0.1.3"]
	Description: The ClojureScript browser-repl, rebuilt stronger, faster, easier.
	URL: http://github.com/cemerick/austin
	License(s): Eclipse Public License (http://www.eclipse.org/legal/epl-v10.html)
	Dependencies: [org.clojure/clojure "1.5.1"]
				  [org.clojure/clojurescript "0.0-2014"]
				  [com.cemerick/piggieback "0.1.2"]
				  [org.clojure/tools.nrepl "0.2.3"]
				  [clojure-complete "0.2.3"]

	Dependency: [org.clojure/clojurescript "0.0-2156"]
	...

## Contributing

Contributions and suggestions are welcome. If you find something
missing or discover a bug please open an [issue][issues]. 

[leiningen-profiles]: https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
[issues]: https://github.com/noprompt/lein-describe/issues
